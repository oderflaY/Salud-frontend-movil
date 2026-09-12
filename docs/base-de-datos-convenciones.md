# Convenciones de base de datos (obligatorias en cada migración)

Estas reglas se fijaron en `db/migrations/0001_bootstrap.sql` y se aplican sin excepción
en los módulos 3 al 11. Repetido aquí para no tener que redescubrirlo migración por
migración.

1. **Dos esquemas.** `app` = tablas reales, nunca expuestas. `api` = vistas y funciones
   RPC, lo único que ve `PGRST_DB_SCHEMAS=api`.

2. **Toda vista en `api` se crea con `WITH (security_invoker = true)`.** Sin esto, una
   vista creada por el rol dueño de la base (superusuario) evalúa RLS con los permisos
   del *dueño de la vista*, no de quien realmente hace la consulta a través de
   PostgREST — es decir, RLS quedaría bypaseado en silencio para todo el mundo. Esto es
   un requisito de seguridad, no de estilo.
   ```sql
   create view api.ejemplo with (security_invoker = true) as select ...;
   ```

3. **RLS se activa en la tabla base (`app.*`), no solo se confía en el `GRANT` de la
   vista.** Defensa en profundidad: si mañana otra vista expone la misma tabla y alguien
   olvida revisar los permisos, la política sigue aplicando.

   Con `security_invoker = true`, los permisos también se revisan contra quien consulta
   — así que además de la política RLS, cada tabla `app.*` que una vista `api.*` toque
   necesita `grant usage on schema app to paciente, medico;` (una sola vez) y
   `grant select/insert/update on app.<tabla> to <rol>;` explícito. Esto NO crea una ruta
   nueva en PostgREST (que solo enruta lo que está en `PGRST_DB_SCHEMAS=api`) — solo
   permite que la vista, al ejecutarse con los permisos de quien pregunta, pueda leer esa
   tabla. El schema `app` sigue sin tener rutas propias.

4. **Políticas RLS leen el JWT vía el GUC que ya inyecta PostgREST**:
   `current_setting('request.jwt.claims', true)::json->>'sub'` (id del usuario) y
   `->>'role'` (rol Postgres al que se hizo `SET ROLE`). El `true` como segundo argumento
   evita un error cuando no hay JWT (rol `anon`).

5. **IDs**: `default app.generar_id('prefijo')`, nunca generados por el cliente ni por
   Axum.

6. **Errores de negocio**: `perform app.lanzar_error(http_status, 'MOTIVO')` dentro de la
   función RPC — nunca un `RAISE EXCEPTION` a mano con un mensaje de texto libre. Ver
   `docs/errores.md`.

7. **Tiempo real**: si una tabla participa en alguno de los 3 streams (mensajes, agenda,
   diario), su función/trigger de escritura termina con
   `perform app.emitir_evento('canal:{id}', 'evento_nombre', jsonb del recurso)`.

8. **Nada de secretos en archivos versionados.** Contraseñas de roles se fijan en
   `scripts/set_secrets.sh` desde variables de entorno, nunca en un `.sql` de
   `db/migrations`.

9. **Cada módulo se cierra con su propio archivo `db/tests/NN_modulo.sql`** (pgTAP):
   constraints, al menos un caso por rol de RLS (qué sí ve, qué no ve), y los casos
   límite de cada función RPC.

10. **Las funciones RPC en `api.*` con más de un parámetro escalar se nombran
    igual que la clave JSON que se espera del cliente, sin prefijo** —
    PostgREST mapea la clave del body directo al nombre del parámetro
    Postgres, no hay una capa de alias. (Una función con un solo parámetro
    `jsonb` es distinta: con el header `Prefer: params=single-object` todo el
    body entra ahí sin importar su nombre — así se hizo con
    `api.registrar_paciente`/`api.actualizar_historial` en el módulo 3). Para
    evitar "column reference is ambiguous" dentro del cuerpo de la función
    cuando un parámetro se llama igual que una columna, toda referencia a
    columnas de tabla dentro de la función debe ir calificada con su alias
    (`t.id_paciente`, no `id_paciente` a secas). Las funciones internas en
    `app.*` (no expuestas a PostgREST) sí pueden seguir usando el prefijo
    `p_` de forma libre, porque a esas nadie les manda JSON por nombre.

    Calificar con `nombre_funcion.parametro` (la forma estándar de PL/pgSQL
    para desambiguar) no alcanza dentro de una **lista de nombres de
    columna** — la lista de `INSERT INTO tabla (col1, col2)` o la de
    `ON CONFLICT (col1, col2)` — porque ahí un identificador igual al
    parámetro sigue dando "column reference is ambiguous" sin importar la
    calificación (bug real en `api.solicitar_vinculacion`, módulo 7). La
    salida que sí funciona: agregar `#variable_conflict use_column` como
    primera línea del cuerpo de la función — así cualquier referencia
    ambigua se resuelve a favor de la columna (lo correcto en una lista de
    columnas, que nunca puede significar otra cosa), y las referencias que sí
    necesitan el valor del parámetro se califican explícito como
    `nombre_funcion.parametro` en las posiciones de expresión (`VALUES (...)`,
    `WHERE ...`), que siguen funcionando igual con el pragma activo.

    Otro choque de nombres, distinto y más estricto: una función con
    `RETURNS TABLE(...)` convierte cada columna de esa tabla en un parámetro
    OUT implícito, en el mismo espacio de nombres que los parámetros IN. Un
    parámetro IN con el mismo nombre que una columna de salida no compila en
    absoluto (`parameter name "x" used more than once`), sin importar
    calificación ni pragma. Cuando el shape de la respuesta necesita un campo
    (ej. `autor`, `instante`) que también tendría sentido como nombre del
    parámetro de entrada, se renombra el parámetro de entrada (`autor` →
    `autor_remitente`, `instante` → `instante_recibido`) — la respuesta
    conserva el nombre de campo del contrato, que es lo que de verdad importa
    (bug real en `api.enviar_mensaje` y `api.obtener_respuesta_automatica`,
    módulo 8).

11. **`SELECT ... FOR UPDATE` bajo RLS exige una política para el comando
    UPDATE, no solo para SELECT** — aunque esa sentencia concreta no
    modifique ninguna columna, Postgres evalúa el bloqueo de fila contra la
    política de UPDATE (la que aplicaría si sí se modificara). Sin ella, la
    fila pasa la política de SELECT normal pero el `FOR UPDATE` la descarta
    en silencio — la función ve "no encontrado" (`FOUND = false`) sobre una
    fila que en realidad existe y es visible, y dispara el motivo de error
    equivocado (bug real: `reservar_temporalmente` reportaba
    `FRANJA_OCUPADA` sobre una franja genuinamente libre, módulo 9, porque
    `app.franjas` solo tenía política de SELECT). Regla práctica: toda tabla
    que participe en un `FOR UPDATE` necesita su propia política `for update`
    (puede ser tan permisiva como la de SELECT si el dato no es sensible),
    independientemente del `GRANT UPDATE` a nivel de tabla/columna.

12. **Una vista con varios `JOIN` es tan permisiva como la política RLS más
    estricta de cualquier tabla en la cadena.** Bug real encontrado en el
    módulo 3: `api.paciente_expediente` parte de `from app.pacientes p ...`;
    la política de 0002 en `app.pacientes` solo dejaba ver "mi propia fila"
    (el caso paciente), así que un médico con vínculo vigente en
    `control_accesos_medico` igual recibía cero filas — la tabla base ya lo
    había filtrado antes de llegar a las demás uniones. Al agregar un nuevo
    camino de acceso (ej. "médico vinculado puede leer"), hay que agregar una
    política PERMISSIVE nueva en **cada tabla de la cadena de joins**,
    empezando por la tabla base del `FROM`, no solo en las tablas hijas. Las
    políticas permisivas del mismo comando se combinan con `OR`, así que
    sumar una política nueva nunca quita acceso que ya existía.
