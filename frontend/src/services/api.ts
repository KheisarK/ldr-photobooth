const API_URL = import.meta.env.VITE_API_URL ?? 'https://ldr-photobooth-production-b840.up.railway.app/api'

type BoothResponse = {
  code: string
  status: string
  shareUrl?: string
}

async function parseResponse(response: Response) {
  if (!response.ok) {
    throw new Error(`Request gagal (${response.status})`)
  }

  return response.json()
}

export async function createBooth(): Promise<BoothResponse> {
  const response = await fetch(`${API_URL}/booths`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
  })

  return parseResponse(response)
}

export async function getBooth(code: string): Promise<BoothResponse> {
  const response = await fetch(`${API_URL}/booths/${encodeURIComponent(code)}`)
  return parseResponse(response)
}

export async function uploadPhotos(code: string, participant: 'a' | 'b', photos: Blob[]) {
  const formData = new FormData()
  formData.append('participant', participant)
  photos.forEach((photo) => formData.append('photos', photo, `photo-${Date.now()}-${Math.random()}.jpg`))

  const response = await fetch(`${API_URL}/booths/${encodeURIComponent(code)}/photos`, {
    method: 'POST',
    body: formData,
  })

  return parseResponse(response)
}
