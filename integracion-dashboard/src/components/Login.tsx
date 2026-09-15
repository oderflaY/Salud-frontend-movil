// Login del dashboard con la cuenta del médico en la app. Estilos en línea
// para que funcione tal cual; cámbialos por los del dashboard.

import { useState, type FormEvent } from 'react';
import { iniciarSesion, mensajeDeError } from '../api/adminApi';
import type { SesionAdmin } from '../types/admin';

export default function Login({ onLogin }: { onLogin: (usuario: SesionAdmin) => void }) {
  const [correo, setCorreo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const { usuario } = await iniciarSesion(correo, contrasena);
      onLogin(usuario);
    } catch (e) {
      setError(mensajeDeError(e));
      setContrasena('');
    } finally {
      setEnviando(false);
    }
  }

  const campo = { width: '100%', padding: '10px 12px', marginTop: 6, borderRadius: 8, border: '1px solid #c9ced6', boxSizing: 'border-box' } as const;

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', fontFamily: 'system-ui', background: '#f4f6f8' }}>
      <form onSubmit={enviar} style={{ width: 360, background: '#fff', padding: 32, borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,.08)' }}>
        <h1 style={{ margin: '0 0 4px', fontSize: 22 }}>Panel del médico</h1>
        <p style={{ margin: '0 0 24px', color: '#5b6470', fontSize: 14 }}>Entra con tu cuenta de la app Salud.</p>
        <label style={{ fontSize: 14 }}>
          Correo
          <input type="email" autoComplete="username" required value={correo} onChange={(e) => setCorreo(e.target.value)} style={campo} />
        </label>
        <label style={{ display: 'block', marginTop: 16, fontSize: 14 }}>
          Contraseña
          <input type="password" autoComplete="current-password" required minLength={8} value={contrasena} onChange={(e) => setContrasena(e.target.value)} style={campo} />
        </label>
        {error && <p role="alert" style={{ color: '#b3261e', fontSize: 14, marginTop: 16 }}>{error}</p>}
        <button type="submit" disabled={enviando} style={{ width: '100%', marginTop: 24, padding: 12, borderRadius: 8, border: 0, background: '#0b6e69', color: '#fff', fontSize: 15, cursor: 'pointer' }}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
