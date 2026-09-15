# Correr la app contra el backend local

Requisito: backend levantado (`sudo docker compose up -d` en la raíz).

## 1. Dile a la app dónde está el backend

En `frontend/local.properties` (no va al repositorio):

```properties
# Emulador: 10.0.2.2 es el localhost de tu máquina (es el valor por defecto)
salud.baseUrl.debug=http://10.0.2.2:8000

# Teléfono físico: la IP de tu máquina en la red local
salud.baseUrl.debug=http://192.168.18.26:8000
```

Tu IP: `ip -4 addr show | grep inet`

Si usas teléfono físico, esa IP también tiene que estar en
`frontend/androidApp/src/debug/res/xml/network_security_config.xml`
(solo builds debug pueden usar HTTP sin cifrar).

## 2. Compilar e instalar

```bash
cd ~/Documents/GitHub/Salud-frontend-movil/frontend
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## 3. Datos de demostración

```bash
sudo bash ~/Documents/GitHub/Salud-frontend-movil/scripts/seed_demo.sh
```

Contraseña de todas las cuentas: `Demo1234`

- Pacientes: `paciente1@salud.local` … `paciente4@salud.local`
- Médicos: `dr.silva@salud.local`, `dra.lopez@salud.local`, `dr.garcia@salud.local`, `dra.torres@salud.local`, `dr.reyes@salud.local`, `dra.moreno@salud.local`
- Tarjeta RFID de la demo: `C38610A8` → Juan Pérez (paciente1)
