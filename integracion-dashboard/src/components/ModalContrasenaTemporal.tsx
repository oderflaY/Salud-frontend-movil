// Muestra el resultado de "Resetear cuenta". La contraseña temporal solo
// existe en esta respuesta: al cerrar el modal ya no se puede recuperar.
//
// Uso:
//   const [reset, setReset] = useState<ResultadoReset | null>(null);
//   const r = await resetCuentaPaciente(paciente.idPaciente, motivo); setReset(r);
//   {reset && <ModalContrasenaTemporal resultado={reset} onCerrar={() => setReset(null)} />}

import { useState } from 'react';
import type { ResultadoReset } from '../types/admin';

export default function ModalContrasenaTemporal({ resultado, onCerrar }: { resultado: ResultadoReset; onCerrar: () => void }) {
  const [copia, setCopia] = useState<'no' | 'si' | 'fallo'>('no');
  const vence = new Date(resultado.expiraEn).toLocaleString();

  // El portapapeles solo existe en https o localhost: abierto por la IP de la red, falla.
  async function copiar() {
    try {
      await navigator.clipboard.writeText(resultado.contrasenaTemporal);
      setCopia('si');
    } catch {
      setCopia('fallo');
    }
  }

  return (
    <div role="dialog" aria-modal="true" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.45)', display: 'grid', placeItems: 'center', fontFamily: 'system-ui', zIndex: 1000 }}>
      <div style={{ width: 420, background: '#fff', padding: 28, borderRadius: 12 }}>
        <h2 style={{ margin: '0 0 12px', fontSize: 20 }}>Contraseña temporal</h2>
        <p style={{ fontFamily: 'ui-monospace, monospace', fontSize: 28, letterSpacing: 2, textAlign: 'center', background: '#f1f4f7', padding: 16, borderRadius: 8, margin: '0 0 12px' }}>
          {resultado.contrasenaTemporal}
        </p>
        <p style={{ fontSize: 14, color: '#3c434d', margin: '0 0 8px' }}>{resultado.instrucciones}</p>
        <p style={{ fontSize: 13, color: '#6b7280', margin: '0 0 20px' }}>Vence: {vence}. Solo se muestra esta vez.</p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <button onClick={copiar}>{copia === 'si' ? 'Copiada' : copia === 'fallo' ? 'Cópiala a mano' : 'Copiar'}</button>
          <button onClick={onCerrar}>Ya se la di al paciente</button>
        </div>
      </div>
    </div>
  );
}
