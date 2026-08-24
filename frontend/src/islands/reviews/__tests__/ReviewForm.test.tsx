import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ReviewForm from '../ReviewForm';
import type { ReviewDto } from '@/lib/api';

/**
 * Characterization tests written before extracting the login-prompt/success screens and the
 * title/label computation out of this component (S3776, complexity 34) -- it had none. Covers the
 * logged-out prompt, loading an existing review for editing ("replacing"), required-field
 * validation, the successful submit/replace paths, and the server-error path.
 */

const createReview = vi.fn();
const getProductReviews = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    createReview: (...args: unknown[]) => createReview(...args),
    getProductReviews: (...args: unknown[]) => getProductReviews(...args),
  };
});

function review(overrides: Partial<ReviewDto> = {}): ReviewDto {
  return {
    id: 'r1',
    productId: 'p1',
    userId: 'u1',
    rating: 4,
    title: 'Buena calidad',
    comment: 'Me encantó',
    approved: true,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  getProductReviews.mockResolvedValue([]);
});

describe('ReviewForm', () => {
  it('prompts to sign in when there is no token', () => {
    render(<ReviewForm productId="p1" locale="es" />);
    expect(screen.getByRole('link', { name: /inicia sesión/i })).toHaveAttribute(
      'href', expect.stringContaining('/es/auth/login?redirect=')
    );
  });

  it('shows the plain write-a-review form for a first-time reviewer', async () => {
    render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" />);
    expect(await screen.findByText('Escribir una reseña')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /publicar reseña/i })).toBeInTheDocument();
  });

  it('preloads an existing review and switches to the replace copy', async () => {
    getProductReviews.mockResolvedValue([review({ userId: 'u1', rating: 5, title: 'Excelente', comment: 'Todo perfecto' })]);
    render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" />);

    expect(await screen.findByText('Actualizar tu reseña')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Excelente')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Todo perfecto')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reemplazar reseña/i })).toBeInTheDocument();
  });

  it('requires a rating and a comment before submitting', async () => {
    const user = userEvent.setup();
    render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" />);
    await screen.findByText('Escribir una reseña');

    await user.click(screen.getByRole('button', { name: /publicar reseña/i }));
    expect(await screen.findByText(/selecciona una puntuación/i)).toBeInTheDocument();
    expect(createReview).not.toHaveBeenCalled();

    await user.click(screen.getAllByRole('button', { name: '' })[3]);
    await user.click(screen.getByRole('button', { name: /publicar reseña/i }));
    expect(await screen.findByText(/el comentario es obligatorio/i)).toBeInTheDocument();
  });

  it('submits a new review with the trimmed fields and shows the thank-you screen', async () => {
    createReview.mockResolvedValue(review());
    const onSubmitted = vi.fn();
    const user = userEvent.setup();
    const { container } = render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" onSubmitted={onSubmitted} />);
    await screen.findByText('Escribir una reseña');

    await user.click(screen.getAllByRole('button', { name: '' })[3]);
    await user.type(container.querySelector('textarea')!, '  Muy bueno  ');
    await user.click(screen.getByRole('button', { name: /publicar reseña/i }));

    expect(createReview).toHaveBeenCalledWith('p1', 'tok', { rating: 4, title: undefined, comment: 'Muy bueno' });
    expect(await screen.findByText('¡Gracias por tu reseña!')).toBeInTheDocument();
    expect(onSubmitted).toHaveBeenCalled();
  });

  it('shows the replacing thank-you copy when updating an existing review', async () => {
    getProductReviews.mockResolvedValue([review({ userId: 'u1' })]);
    createReview.mockResolvedValue(review());
    const user = userEvent.setup();
    render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" />);
    await screen.findByText('Actualizar tu reseña');

    await user.click(screen.getByRole('button', { name: /reemplazar reseña/i }));
    expect(await screen.findByText('¡Gracias! Actualizamos tu reseña.')).toBeInTheDocument();
  });

  it('shows a server error and lets the customer retry', async () => {
    createReview.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    const { container } = render(<ReviewForm productId="p1" token="tok" userId="u1" locale="es" />);
    await screen.findByText('Escribir una reseña');

    await user.click(screen.getAllByRole('button', { name: '' })[3]);
    await user.type(container.querySelector('textarea')!, 'Todo bien');
    await user.click(screen.getByRole('button', { name: /publicar reseña/i }));

    expect(await screen.findByText(/error al enviar/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /publicar reseña/i })).toBeInTheDocument();
  });

  it('renders the English copy', async () => {
    render(<ReviewForm productId="p1" token="tok" userId="u1" locale="en" />);
    expect(await screen.findByText('Write a review')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit review/i })).toBeInTheDocument();
  });
});
