# Conectar Salud-Frontend (dashboard del médico) al backend

Estos archivos se copian tal cual dentro del repo **Salud-Frontend**. Compilan con TypeScript en modo estricto, con React 19 y Vite 6.

| Archivo | Qué es |
|---|---|
| `src/types/admin.ts` | Reemplaza al actual. Los tipos son exactamente lo que manda el backend. |
| `src/api/adminApi.ts` | Reemplaza al actual. Tiene las mismas funciones (`getSesion`, `getKpis`, `getPacientes`, `resetCuentaPaciente`, `getAlertas`, `actualizarEstadoAlerta`, `getAuditoria`) más `iniciarSesion`, `cerrarSesion` y `mensajeDeError`. |
| `src/components/SesionGate.tsx` | Muestra el login si no hay sesión, y regresa a él si la sesión vence. |
| `src/components/Login.tsx` | Pantalla de login, con la cuenta del médico en la app. |
| `src/components/ModalContrasenaTemporal.tsx` | Muestra la contraseña temporal después de un reset. |
| `.env.example` | Copiarlo como `.env`. |

## Pasos

1. **Levantar el backend** en la computadora del Docker (una vez, tras actualizar):
   ```bash
   cd ~/Documents/GitHub/Salud-frontend-movil && sudo docker compose up migrate && sudo docker compose up -d --build && sudo bash scripts/seed_demo.sh
   ```
   Comprobar que responde: `curl http://localhost:8081/healthz` → `ok`.

2. **Copiar los archivos** al dashboard (ajusta la ruta de Salud-Frontend):
   ```bash
   cp -r ~/Documents/GitHub/Salud-frontend-movil/integracion-dashboard/src/. ~/ruta/a/Salud-Frontend/src/ && cp ~/Documents/GitHub/Salud-frontend-movil/integracion-dashboard/.env.example ~/ruta/a/Salud-Frontend/.env
   ```

3. **Envolver la app con el login** en `src/main.tsx`:
   ```tsx
   import SesionGate from './components/SesionGate';
   // ...
   <SesionGate><App /></SesionGate>
   ```
   Dentro, cualquier componente obtiene al médico con `const { usuario, salir } = useSesion();` (se importa de `./components/SesionGate`). Por ejemplo, un botón "Salir" es `onClick={salir}`.

4. **Quitar el respaldo a `mockAdmin.ts`.** El `adminApi.ts` nuevo ya no lo usa, así que ningún componente debe importarlo. Con este backend, un 404 es una respuesta real (un paciente que no es tuyo), no "no hay servidor".

5. **Ajustar los componentes a los nombres nuevos.** `tsc` marca cada campo que ya no existe. Correr `npx tsc --noEmit` y corregir campo por campo con `src/types/admin.ts` a la vista.

6. **Reset de cuenta** en la tabla de pacientes:
   ```tsx
   const [reset, setReset] = useState<ResultadoReset | null>(null);
   async function resetear(p: Paciente) {
     if (!confirm(`¿Resetear la cuenta de ${p.nombreCompleto} (${p.correoEnmascarado})?`)) return;
     try { setReset(await resetCuentaPaciente(p.idPaciente, 'Olvidó su contraseña')); }
     catch (e) { alert(mensajeDeError(e)); }
   }
   // ...
   {reset && <ModalContrasenaTemporal resultado={reset} onCerrar={() => setReset(null)} />}
   ```

7. `npm run dev` y entrar con **`dr.silva@salud.local` / `Demo1234`**.

## Si no conecta

| Síntoma | Causa y arreglo |
|---|---|
| "No hay conexión con el servidor" | El backend no está arriba, o `VITE_API_URL` apunta mal. Probar la URL con curl y reiniciar `npm run dev` después de cambiar el `.env`. |
| En la consola del navegador: *blocked by CORS policy* | El dashboard corre en un origen que el backend no conoce. Por defecto se permiten `http://localhost:5173` y `:4173`. Si abres el dashboard desde otra máquina (p. ej. `http://192.168.18.26:5173`), agrega ese origen en el `.env` **del backend**: `CORS_ORIGENES=http://localhost:5173,http://192.168.18.26:5173`, y luego `sudo docker compose up -d backend`. |
| 404 en `/api/v1/...` | El backend en Docker es el viejo: repetir el paso 1. |
| El login dice "El correo o la contraseña no coinciden" | Volver a sembrar la demo (`sudo bash scripts/seed_demo.sh`). Todas las cuentas usan `Demo1234`. |
| Lista de pacientes vacía | Ese médico no tiene pacientes vinculados. `dr.silva` tiene a paciente1 y paciente2. |

El contrato completo (qué hace cada ruta, cómo se generan las alertas, tabla de errores) está en `docs/dashboard-medico.md`.
