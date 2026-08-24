const API_URL = (import.meta.env.VITE_API_URL ?? 'https://ldr-photobooth-production-b840.up.railway.app/api').replace(/\/$/, '')

export type Participant = 'a' | 'b'
export type BoothStatus = 'WAITING_A' | 'WAITING_B' | 'READY_TO_FINALIZE' | 'COMPLETED'
export type BoothMode = 'REFERENCE' | 'SURPRISE'
export type FrameStyle = 'CLASSIC' | 'POLAROID' | 'MIDNIGHT'

export type BoothResponse = {
  code: string
  status: BoothStatus
  mode: BoothMode
  frameStyle?: FrameStyle | null
  expiresAt?: string
  shareUrl?: string
  photoCounts?: {
    a: number
    b: number
  }
  resultUrl?: string | null
  ownerToken?: string
}

type ApiErrorBody = {
  error?: {
    code?: string
    message?: string
  }
}

function defaultErrorMessage(status: number) {
  if (status === 404) return 'Room tidak ditemukan. Periksa lagi kode yang kamu masukkan.'
  if (status === 409) return 'Room belum siap untuk langkah ini. Status room akan diperbarui otomatis.'
  if (status === 413) return 'Ukuran foto terlalu besar. Coba ambil foto ulang.'
  if (status === 422) return 'Foto tidak dapat diproses. Pastikan jumlahnya tepat 4 foto.'
  if (status >= 500) return 'Server sedang bermasalah. Coba lagi sebentar.'
  return 'Permintaan gagal. Silakan coba lagi.'
}

async function request(input: RequestInfo | URL, init?: RequestInit, timeoutMs = 20_000) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('Koneksi terlalu lama. Periksa internet kamu lalu coba lagi.')
    }
    throw new Error('Tidak dapat terhubung ke server. Periksa koneksi internet kamu.')
  } finally {
    window.clearTimeout(timeout)
  }
}

async function parseJsonResponse<T>(response: Response): Promise<T> {
  if (response.ok) return response.json() as Promise<T>

  let body: ApiErrorBody | null = null
  try {
    body = await response.json() as ApiErrorBody
  } catch {
    // Some proxy errors return HTML instead of the API's normal JSON shape.
  }

  const serverMessage = body?.error?.message
  const safeServerMessage = serverMessage && !/^This participant/i.test(serverMessage)
    ? serverMessage
    : null
  throw new Error(safeServerMessage ?? defaultErrorMessage(response.status))
}

export async function createBooth(mode: BoothMode): Promise<BoothResponse> {
  const response = await request(`${API_URL}/booths`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode }),
  })

  return parseJsonResponse<BoothResponse>(response)
}

export async function getBooth(code: string): Promise<BoothResponse> {
  const response = await request(`${API_URL}/booths/${encodeURIComponent(code)}`)
  return parseJsonResponse<BoothResponse>(response)
}

export async function finalizeBooth(code: string, ownerToken: string, frame: FrameStyle): Promise<BoothResponse> {
  const response = await request(`${API_URL}/booths/${encodeURIComponent(code)}/finalize`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Booth-Owner-Token': ownerToken },
    body: JSON.stringify({ frame }),
  }, 90_000)
  return parseJsonResponse<BoothResponse>(response)
}

export function getReferencePhotoUrl(code: string, index: number) {
  return `${API_URL}/booths/${encodeURIComponent(code)}/reference/${index}`
}

export async function deleteBooth(code: string, ownerToken: string): Promise<void> {
  const response = await request(`${API_URL}/booths/${encodeURIComponent(code)}`, {
    method: 'DELETE',
    headers: { 'X-Booth-Owner-Token': ownerToken },
  })

  if (!response.ok) await parseJsonResponse<never>(response)
}

export async function uploadPhotos(code: string, participant: Participant, photos: Blob[]): Promise<BoothResponse> {
  if (photos.length !== 4) {
    throw new Error('Kamu harus mengambil tepat 4 foto sebelum mengunggah.')
  }

  const formData = new FormData()
  formData.append('participant', participant)
  photos.forEach((photo, index) => {
    formData.append('photos', photo, `${code}-${participant}-${index + 1}.jpg`)
  })

  const response = await request(`${API_URL}/booths/${encodeURIComponent(code)}/photos`, {
    method: 'POST',
    body: formData,
  }, 90_000)

  return parseJsonResponse<BoothResponse>(response)
}

export function getPhotostripUrl(code: string, resultUrl?: string | null) {
  if (resultUrl?.startsWith('http://') || resultUrl?.startsWith('https://')) return resultUrl

  const apiOrigin = new URL(API_URL).origin
  return resultUrl
    ? new URL(resultUrl, apiOrigin).toString()
    : `${API_URL}/booths/${encodeURIComponent(code)}/result`
}

export function getPhotostripDownloadUrl(code: string, resultUrl?: string | null) {
  const url = new URL(getPhotostripUrl(code, resultUrl))
  url.searchParams.set('download', 'true')
  return url.toString()
}
