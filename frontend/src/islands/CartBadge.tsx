import { useCartStore } from '../lib/cartStore';

export default function CartBadge() {
  // Select items array so the component re-renders when items change
  const items = useCartStore((s) => s.items);
  const count = items.reduce((sum, i) => sum + i.quantity, 0);

  if (count === 0) return null;

  return (
    <span
      className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-pe-gold text-pe-black text-[10px] font-bold leading-none"
      aria-label={`${count} items en el carrito`}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}
