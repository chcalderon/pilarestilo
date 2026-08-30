import { useState } from 'react';
import type { VariantFieldDto, VariantFieldInputType } from '../../lib/api';

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

function parseOptions(raw: string): string[] {
  return raw.split(',').map((v) => v.trim()).filter(Boolean);
}

/**
 * The stored model is a string[]; the field is a comma-separated line. Deriving the input value
 * straight from `options.join(', ')` ate every comma the moment it was typed — `split`/`filter`
 * dropped the empty tail before the next character arrived. So the raw text is buffered locally
 * while editing and only normalised back on blur; the parsed array still flows up on every keystroke.
 */
function OptionsInput({
  options, onOptionsChange,
}: { readonly options: string[]; readonly onOptionsChange: (next: string[]) => void }) {
  const [text, setText] = useState(options.join(', '));

  return (
    <input
      className={INPUT_CLASS}
      value={text}
      onChange={(e) => {
        setText(e.target.value);
        onOptionsChange(parseOptions(e.target.value));
      }}
      onBlur={() => setText(options.join(', '))}
      placeholder="XS, S, M, L, XL"
    />
  );
}

export default function VariantFieldEditor({
  fieldNumber, field, onChange,
}: { readonly fieldNumber: 1 | 2; readonly field: VariantFieldDto; readonly onChange: (next: VariantFieldDto) => void }) {
  return (
    <div className="flex flex-col gap-1 border border-pe-black/10 p-2">
      <label className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted">
        Etiqueta campo {fieldNumber}
      </label>
      <input className={INPUT_CLASS} value={field.label}
        onChange={(e) => onChange({ ...field, label: e.target.value })} placeholder={fieldNumber === 1 ? 'Color' : 'Talla'} />
      <select className={INPUT_CLASS} value={field.inputType}
        onChange={(e) => onChange({ ...field, inputType: e.target.value as VariantFieldInputType })}>
        <option value="FREE_TEXT">Texto libre</option>
        <option value="OPTIONS">Lista de opciones</option>
        <option value="RANGE">Rango numérico</option>
      </select>
      {field.inputType === 'OPTIONS' && (
        <OptionsInput options={field.options} onOptionsChange={(options) => onChange({ ...field, options })} />
      )}
      {field.inputType === 'RANGE' && (
        <div className="flex gap-2">
          <input type="number" className={INPUT_CLASS} value={field.min ?? ''}
            onChange={(e) => onChange({ ...field, min: e.target.value === '' ? null : Number(e.target.value) })} placeholder="Min" />
          <input type="number" className={INPUT_CLASS} value={field.max ?? ''}
            onChange={(e) => onChange({ ...field, max: e.target.value === '' ? null : Number(e.target.value) })} placeholder="Max" />
        </div>
      )}
      <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
        <input type="checkbox" checked={field.allowMultiple}
          onChange={(e) => onChange({ ...field, allowMultiple: e.target.checked })} />
        <span>Permitir combinar varios valores en una variante</span>
      </label>
      {field.inputType !== 'FREE_TEXT' && (
        <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
          <input type="checkbox" checked={field.allowCustom}
            onChange={(e) => onChange({ ...field, allowCustom: e.target.checked })} />
          <span>Permitir un valor fuera de la lista</span>
        </label>
      )}
    </div>
  );
}
