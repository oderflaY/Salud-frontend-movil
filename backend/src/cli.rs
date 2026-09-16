//! `backend crear-admin <correo> <nombre> <apellidos>`: crea (o restablece) una
//! cuenta de administrador del panel. No hay ruta HTTP para esto a propósito:
//! el primer administrador solo puede crearlo quien tiene acceso al servidor.
//!
//! La contraseña se toma de `ADMIN_CONTRASENA`. Sin ella se genera una
//! temporal, que el panel pide cambiar al entrar.

use std::{env, process};

use crate::{auth::handlers::validar_contrasena_nueva, auth::password, config::Config, dashboard::contrasena_temporal, db};

pub async fn crear_admin(argumentos: &[String]) {
    let [correo, nombre, apellidos] = argumentos else {
        eprintln!("Uso: backend crear-admin <correo> <nombre> <apellidos>");
        eprintln!("  Contraseña en ADMIN_CONTRASENA; sin ella se genera una temporal.");
        process::exit(2);
    };
    let correo = correo.trim().to_lowercase();
    if !correo.contains('@') || nombre.trim().is_empty() || apellidos.trim().is_empty() {
        eprintln!("Correo, nombre y apellidos son obligatorios.");
        process::exit(2);
    }

    let (contrasena, es_temporal) = match env::var("ADMIN_CONTRASENA").ok().filter(|c| !c.is_empty()) {
        Some(c) => {
            if validar_contrasena_nueva(&c).is_err() {
                eprintln!("ADMIN_CONTRASENA debe tener entre 8 y 128 caracteres.");
                process::exit(2);
            }
            (c, false)
        }
        None => (contrasena_temporal::generar(), true),
    };

    let config = Config::from_env();
    let pool = db::conectar(&config.database_url)
        .await
        .expect("no se pudo conectar a la base de datos");
    let hash = password::hash(&contrasena, config.argon2_secret_key.as_bytes()).expect("no se pudo cifrar la contraseña");

    let id: String = sqlx::query_scalar(
        "insert into app.administradores
           (correo, hash_contrasena, nombre, apellidos, debe_cambiar_contrasena, contrasena_temporal_expira_en)
         values ($1, $2, $3, $4, $5,
                 case when $5 then now() + make_interval(hours => app.config_entero('horas_validez_contrasena_temporal')) end)
         on conflict (correo) do update
            set hash_contrasena = excluded.hash_contrasena,
                nombre = excluded.nombre,
                apellidos = excluded.apellidos,
                estado_cuenta = 'activa',
                debe_cambiar_contrasena = excluded.debe_cambiar_contrasena,
                contrasena_temporal_expira_en = excluded.contrasena_temporal_expira_en,
                sesiones_validas_desde = now()
         returning id_admin",
    )
    .bind(&correo)
    .bind(&hash)
    .bind(nombre.trim())
    .bind(apellidos.trim())
    .bind(es_temporal)
    .fetch_one(&pool)
    .await
    .expect("no se pudo guardar el administrador (¿aplicaste la migración 0018?)");

    println!("Administrador listo: {correo} ({id})");
    if es_temporal {
        println!("Contraseña temporal: {contrasena}");
        println!("El panel pedirá cambiarla al entrar. Vence en 72 horas.");
    }
}
