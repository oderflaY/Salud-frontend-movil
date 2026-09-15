// Envuelve la app: sin sesión muestra el login, con sesión muestra el
// dashboard y reparte el médico en sesión por `useSesion()`. Si el backend
// cierra la sesión (token vencido o cuenta bloqueada), vuelve solo al login.
//
// Uso en main.tsx:   <SesionGate><App /></SesionGate>

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { alPerderSesion, cerrarSesion, ErrorApi, getSesion, mensajeDeError, obtenerToken } from '../api/adminApi';
import type { SesionAdmin } from '../types/admin';
import Login from './Login';

interface ValorSesion {
  usuario: SesionAdmin;
  salir: () => void;
}

const SesionContext = createContext<ValorSesion | null>(null);

/** El médico en sesión. Solo dentro de `<SesionGate>`. */
export function useSesion(): ValorSesion {
  const valor = useContext(SesionContext);
  if (!valor) throw new Error('useSesion() se usa dentro de <SesionGate>');
  return valor;
}

type Estado =
  | { tipo: 'comprobando' }
  | { tipo: 'fuera' }
  | { tipo: 'sin-conexion'; mensaje: string }
  | { tipo: 'dentro'; usuario: SesionAdmin };

export default function SesionGate({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>(obtenerToken() ? { tipo: 'comprobando' } : { tipo: 'fuera' });

  useEffect(() => alPerderSesion(() => setEstado({ tipo: 'fuera' })), []);

  const comprobar = useCallback(() => {
    setEstado({ tipo: 'comprobando' });
    getSesion()
      .then((usuario) => setEstado({ tipo: 'dentro', usuario }))
      .catch((error: unknown) => {
        // Un error del servidor (401/403) invalida el token; sin red, no.
        if (error instanceof ErrorApi) {
          cerrarSesion();
          setEstado({ tipo: 'fuera' });
        } else {
          setEstado({ tipo: 'sin-conexion', mensaje: mensajeDeError(error) });
        }
      });
  }, []);

  useEffect(() => {
    if (obtenerToken()) comprobar();
  }, [comprobar]);

  switch (estado.tipo) {
    case 'comprobando':
      return <p style={{ padding: 32, fontFamily: 'system-ui' }}>Cargando…</p>;
    case 'sin-conexion':
      return (
        <div style={{ padding: 32, fontFamily: 'system-ui' }}>
          <p>{estado.mensaje}</p>
          <button onClick={comprobar}>Reintentar</button>
        </div>
      );
    case 'fuera':
      return <Login onLogin={(usuario) => setEstado({ tipo: 'dentro', usuario })} />;
    case 'dentro':
      return (
        <SesionContext.Provider value={{ usuario: estado.usuario, salir: cerrarSesion }}>
          {children}
        </SesionContext.Provider>
      );
  }
}
