//! Integración de punta a punta para los módulos 1 y 2 (autenticación):
//! levanta un Postgres real efímero con testcontainers, aplica las
//! migraciones reales de `db/migrations`, monta el `Router` de Axum tal cual
//! lo sirve producción, y le pega peticiones HTTP con `tower::ServiceExt::oneshot`
//! (sin abrir un socket real). Esto es lo más cercano a "funciona con todos
//! los endpoints" que se puede verificar sin el stack completo de Docker.

use std::{fs, path::Path, sync::Arc};

use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use backend::{config::Config, routes, state::AppState};
use http_body_util::BodyExt;
use serde_json::{json, Value};
use sqlx::postgres::PgPoolOptions;
use testcontainers::{ContainerAsync, ImageExt};
use testcontainers_modules::{postgres::Postgres, testcontainers::runners::AsyncRunner};
use tower::ServiceExt;

async fn preparar_entorno() -> (ContainerAsync<Postgres>, Router) {
    // Se fija la misma major version que usa producción (db/Dockerfile,
    // postgres:16-bookworm): la imagen por defecto de testcontainers-modules
    // es más vieja y no trae `gen_random_uuid()` nativo, que 0001_bootstrap.sql
    // sí asume disponible desde Postgres 13+.
    let contenedor = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("no se pudo iniciar el contenedor de Postgres");
    let puerto = contenedor
        .get_host_port_ipv4(5432)
        .await
        .expect("no se pudo obtener el puerto publicado");
    // Normalmente el runner de pruebas y el contenedor comparten la misma
    // red (127.0.0.1). Si el propio `cargo test` corre dentro de otro
    // contenedor (Docker-outside-of-Docker, socket montado), el puerto se
    // publica en el host real de Docker, no en el loopback de este
    // contenedor — de ahí el override para ese caso puntual.
    let host_bd = std::env::var("TEST_DB_HOST").unwrap_or_else(|_| "127.0.0.1".to_string());
    let database_url = format!("postgres://postgres:postgres@{host_bd}:{puerto}/postgres");

    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("no se pudo conectar al Postgres de prueba");

    aplicar_migraciones(&pool).await;

    let config = Config {
        database_url,
        jwt_secret: "secreto-de-pruebas-suficientemente-largo".to_string(),
        jwt_expiracion_horas: 12,
        argon2_secret_key: "pepper-de-pruebas".to_string(),
        backend_port: 0,
        deepseek_api_key: None,
        deepseek_base_url: "https://api.deepseek.com".to_string(),
        deepseek_modelo: "deepseek-chat".to_string(),
    };

    let state = AppState {
        db: pool,
        config: Arc::new(config),
        hub: backend::realtime::hub::Hub::nuevo(),
        http: reqwest::Client::new(),
    };

    (contenedor, routes::construir(state))
}

async fn aplicar_migraciones(pool: &sqlx::PgPool) {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../db/migrations");
    let mut archivos: Vec<_> = fs::read_dir(&dir)
        .unwrap_or_else(|e| panic!("no se pudo leer el directorio {dir:?}: {e}"))
        .filter_map(|entrada| entrada.ok())
        .map(|entrada| entrada.path())
        .filter(|ruta| ruta.extension().is_some_and(|ext| ext == "sql"))
        .collect();
    archivos.sort();

    for archivo in archivos {
        let sql = fs::read_to_string(&archivo)
            .unwrap_or_else(|e| panic!("no se pudo leer {archivo:?}: {e}"));
        sqlx::raw_sql(&sql)
            .execute(pool)
            .await
            .unwrap_or_else(|e| panic!("la migración {archivo:?} falló: {e}"));
    }
}

fn peticion_json(metodo: &str, uri: &str, cuerpo: Value) -> Request<Body> {
    Request::builder()
        .method(metodo)
        .uri(uri)
        .header("content-type", "application/json")
        .body(Body::from(cuerpo.to_string()))
        .unwrap()
}

async fn cuerpo_json(respuesta: axum::response::Response) -> Value {
    let bytes = respuesta.into_body().collect().await.unwrap().to_bytes();
    serde_json::from_slice(&bytes).expect("la respuesta no fue JSON válido")
}

#[tokio::test]
async fn alta_e_inicio_de_sesion_de_paciente_funciona_de_punta_a_punta() {
    let (_contenedor, app) = preparar_entorno().await;

    let correo = "paciente.integracion@example.com";
    let alta = json!({ "correo": correo, "contrasena": "clave-super-segura" });

    let respuesta = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/pacientes", alta.clone()))
        .await
        .unwrap();
    assert_eq!(respuesta.status(), StatusCode::OK);
    let sesion = cuerpo_json(respuesta).await;
    assert!(sesion["idPaciente"].as_str().unwrap().starts_with("pac_"));
    assert!(!sesion["token"].as_str().unwrap().is_empty());
    assert_eq!(sesion["requiereOnboarding"], true);

    // Repetir el alta con el mismo correo debe fallar: CORREO_YA_REGISTRADO / 409.
    let respuesta_dup = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/pacientes", alta.clone()))
        .await
        .unwrap();
    assert_eq!(respuesta_dup.status(), StatusCode::CONFLICT);
    assert_eq!(
        cuerpo_json(respuesta_dup).await["message"],
        "CORREO_YA_REGISTRADO"
    );

    // Iniciar sesión con la contraseña correcta funciona.
    let respuesta_login = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/pacientes/sesion", alta))
        .await
        .unwrap();
    assert_eq!(respuesta_login.status(), StatusCode::OK);

    // Contraseña incorrecta -> CREDENCIALES_INVALIDAS / 401.
    let mala = json!({ "correo": correo, "contrasena": "clave-incorrecta" });
    let respuesta_mala = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/pacientes/sesion", mala))
        .await
        .unwrap();
    assert_eq!(respuesta_mala.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(
        cuerpo_json(respuesta_mala).await["message"],
        "CREDENCIALES_INVALIDAS"
    );

    // Correo inexistente -> también CREDENCIALES_INVALIDAS / 401 (no filtrar
    // si el correo existe o no).
    let inexistente = json!({ "correo": "no-existe@example.com", "contrasena": "algo" });
    let respuesta_inexistente = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/pacientes/sesion", inexistente))
        .await
        .unwrap();
    assert_eq!(respuesta_inexistente.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn alta_de_profesional_valida_tratamiento_y_unicidad_de_cedula() {
    let (_contenedor, app) = preparar_entorno().await;

    let alta = json!({
        "correo": "medico.integracion@example.com",
        "contrasena": "clave-super-segura",
        "nombre": "Elena",
        "apellidos": "Ruiz Santos",
        "tratamiento": "Dra.",
        "cedulaProfesional": "CED-1234"
    });

    let respuesta = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/profesionales", alta))
        .await
        .unwrap();
    assert_eq!(respuesta.status(), StatusCode::OK);
    let sesion = cuerpo_json(respuesta).await;
    assert_eq!(sesion["estadoVerificacion"], "PENDIENTE");
    assert!(sesion["idMedico"].as_str().unwrap().starts_with("doc_"));

    // Misma cédula con otro correo -> CEDULA_YA_REGISTRADA / 409.
    let dup_cedula = json!({
        "correo": "otro.medico@example.com",
        "contrasena": "clave-super-segura",
        "nombre": "Juan",
        "apellidos": "Pérez",
        "tratamiento": "Dr.",
        "cedulaProfesional": "CED-1234"
    });
    let respuesta_dup = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/profesionales", dup_cedula))
        .await
        .unwrap();
    assert_eq!(respuesta_dup.status(), StatusCode::CONFLICT);
    assert_eq!(
        cuerpo_json(respuesta_dup).await["message"],
        "CEDULA_YA_REGISTRADA"
    );

    // tratamiento fuera del dominio Dr./Dra./Dr(a). -> SOLICITUD_INVALIDA / 400.
    let mal_tratamiento = json!({
        "correo": "tercer.medico@example.com",
        "contrasena": "clave-super-segura",
        "nombre": "Ana",
        "apellidos": "Gómez",
        "tratamiento": "Sr.",
        "cedulaProfesional": "CED-9999"
    });
    let respuesta_mal = app
        .clone()
        .oneshot(peticion_json("POST", "/auth/profesionales", mal_tratamiento))
        .await
        .unwrap();
    assert_eq!(respuesta_mal.status(), StatusCode::BAD_REQUEST);
}
