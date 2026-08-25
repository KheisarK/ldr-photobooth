import { useEffect, useRef, useState } from 'react'
import type { FormEvent, MouseEvent as ReactMouseEvent, SyntheticEvent } from 'react'
import {
  createBooth,
  deleteBooth,
  finalizeBooth,
  getBooth,
  getPhotostripDownloadUrl,
  getPhotostripUrl,
  getReferencePhotoUrl,
  uploadPhotos,
} from './services/api'
import type { BoothMode, BoothResponse, FrameStyle, Participant } from './services/api'
import './App.css'

const PHOTO_TOTAL = 4
const POLL_INTERVAL_MS = 3_000

type CameraState = 'idle' | 'requesting' | 'ready' | 'error'
type HomeErrorLocation = 'hero' | 'join' | null

type CapturedPhoto = {
  blob: Blob
  preview: string
}

function boothCodeFromPath() {
  const match = window.location.pathname.match(/^\/booths\/([a-z0-9]+)\/?$/i)
  return match?.[1]?.toUpperCase() ?? ''
}

function roleKey(code: string) {
  return `ldr-photobooth:${code}:participant`
}

function ownerTokenKey(code: string) {
  return `ldr-photobooth:${code}:owner-token`
}

function cameraErrorMessage(error: unknown) {
  if (!(error instanceof DOMException)) {
    return 'Kamera tidak dapat dibuka. Coba muat ulang halaman.'
  }

  if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
    return 'Izin kamera ditolak. Izinkan akses kamera dari pengaturan browser, lalu coba lagi.'
  }
  if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
    return 'Kamera tidak ditemukan di perangkat ini.'
  }
  if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
    return 'Kamera sedang dipakai aplikasi lain. Tutup aplikasi tersebut, lalu coba lagi.'
  }
  if (error.name === 'OverconstrainedError') {
    return 'Kamera tidak mendukung pengaturan yang dibutuhkan.'
  }

  return 'Kamera tidak dapat dibuka. Periksa izin kamera lalu coba lagi.'
}

async function copyToClipboard(value: string) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(value)
    return
  }

  const input = document.createElement('textarea')
  input.value = value
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  document.execCommand('copy')
  input.remove()
}

function App() {
  const initialCode = useRef(boothCodeFromPath())
  const [roomCode, setRoomCode] = useState('')
  const [roomInput, setRoomInput] = useState('')
  const [booth, setBooth] = useState<BoothResponse | null>(null)
  const [participant, setParticipant] = useState<Participant | null>(null)
  const [isLoadingRoom, setIsLoadingRoom] = useState(Boolean(initialCode.current))
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [pollMessage, setPollMessage] = useState('')
  const [actionMessage, setActionMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [homeErrorLocation, setHomeErrorLocation] = useState<HomeErrorLocation>(null)
  const [copiedValue, setCopiedValue] = useState<'code' | 'link' | null>(null)
  const [selectedMode, setSelectedMode] = useState<BoothMode>('REFERENCE')
  const [selectedFrame, setSelectedFrame] = useState<FrameStyle>('CLASSIC')

  const [cameraOpen, setCameraOpen] = useState(false)
  const [cameraState, setCameraState] = useState<CameraState>('idle')
  const [cameraMessage, setCameraMessage] = useState('')
  const [cameraAttempt, setCameraAttempt] = useState(0)
  const [countdown, setCountdown] = useState<number | null>(null)
  const [isCapturing, setIsCapturing] = useState(false)
  const [capturedPhotos, setCapturedPhotos] = useState<CapturedPhoto[]>([])
  const [isUploading, setIsUploading] = useState(false)

  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const captureRunRef = useRef(0)
  const capturedPhotosRef = useRef<CapturedPhoto[]>([])
  const joinDialogRef = useRef<HTMLDialogElement>(null)

  const rememberRole = (code: string, role: Participant) => {
    try {
      window.localStorage.setItem(roleKey(code), role)
    } catch {
      // The flow still works when storage is blocked by the browser.
    }
  }

  const rememberOwnerToken = (code: string, ownerToken?: string) => {
    if (!ownerToken) return
    try {
      window.localStorage.setItem(ownerTokenKey(code), ownerToken)
    } catch {
      // Creating and sharing the room still works when storage is blocked.
    }
  }

  const storedOwnerToken = (code: string) => {
    try {
      return window.localStorage.getItem(ownerTokenKey(code)) ?? ''
    } catch {
      return ''
    }
  }

  const storedRole = (code: string): Participant => {
    try {
      return window.localStorage.getItem(roleKey(code)) === 'a' ? 'a' : 'b'
    } catch {
      return 'b'
    }
  }

  const setActiveBooth = (summary: BoothResponse, role: Participant, updateUrl = true) => {
    const code = summary.code.toUpperCase()
    setBooth(summary)
    setRoomCode(code)
    setParticipant(role)
    setRoomInput('')
    setErrorMessage('')
    setHomeErrorLocation(null)
    setPollMessage('')
    rememberRole(code, role)
    if (updateUrl) window.history.pushState({}, '', `/booths/${code}`)
  }

  const stopCamera = () => {
    captureRunRef.current += 1
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (videoRef.current) videoRef.current.srcObject = null
    setCountdown(null)
    setIsCapturing(false)
    setCameraState('idle')
  }

  const clearCapturedPhotos = () => {
    setCapturedPhotos((current) => {
      current.forEach((photo) => URL.revokeObjectURL(photo.preview))
      return []
    })
  }

  const resetToHome = (updateUrl = true) => {
    stopCamera()
    clearCapturedPhotos()
    setRoomCode('')
    setBooth(null)
    setParticipant(null)
    setCameraOpen(false)
    setActionMessage('')
    setErrorMessage('')
    setHomeErrorLocation(null)
    setPollMessage('')
    setIsLoadingRoom(false)
    if (updateUrl) window.history.pushState({}, '', '/')
  }

  const handleDeleteRoom = async () => {
    const ownerToken = storedOwnerToken(roomCode)
    if (!roomCode || participant !== 'a' || !ownerToken) return
    if (!window.confirm('Hapus room ini beserta semua foto dan photostrip? Tindakan ini tidak bisa dibatalkan.')) return

    setIsSubmitting(true)
    setErrorMessage('')
    try {
      await deleteBooth(roomCode, ownerToken)
      try {
        window.localStorage.removeItem(roleKey(roomCode))
        window.localStorage.removeItem(ownerTokenKey(roomCode))
      } catch {
        // The room has already been deleted from the server.
      }
      resetToHome()
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Room gagal dihapus. Coba lagi.')
    } finally {
      setIsSubmitting(false)
    }
  }

  useEffect(() => {
    capturedPhotosRef.current = capturedPhotos
  }, [capturedPhotos])

  useEffect(() => () => {
    capturedPhotosRef.current.forEach((photo) => URL.revokeObjectURL(photo.preview))
    streamRef.current?.getTracks().forEach((track) => track.stop())
  }, [])

  useEffect(() => {
    const code = initialCode.current
    if (!code) return

    let active = true
    setIsLoadingRoom(true)
    getBooth(code)
      .then((summary) => {
        if (active) setActiveBooth(summary, storedRole(code), false)
      })
      .catch((error) => {
        if (!active) return
        setHomeErrorLocation('join')
        setErrorMessage(error instanceof Error ? error.message : 'Room tidak dapat dibuka.')
        setRoomCode('')
        setBooth(null)
      })
      .finally(() => {
        if (active) setIsLoadingRoom(false)
      })

    return () => { active = false }
    // The deep link only needs to be resolved when the app first opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const handleBackButton = () => {
      const code = boothCodeFromPath()
      if (!code) {
        resetToHome(false)
        return
      }

      setIsLoadingRoom(true)
      getBooth(code)
        .then((summary) => setActiveBooth(summary, storedRole(code), false))
        .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'Room tidak dapat dibuka.'))
        .finally(() => setIsLoadingRoom(false))
    }

    window.addEventListener('popstate', handleBackButton)
    return () => window.removeEventListener('popstate', handleBackButton)
    // This listener intentionally reads the current URL whenever popstate fires.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!roomCode || booth?.status === 'COMPLETED') return

    let active = true
    const refresh = async () => {
      if (document.visibilityState === 'hidden') return
      try {
        const summary = await getBooth(roomCode)
        if (!active) return
        setBooth(summary)
        setPollMessage('')
      } catch {
        if (active) setPollMessage('Status belum bisa diperbarui. Mencoba lagi...')
      }
    }

    const interval = window.setInterval(refresh, POLL_INTERVAL_MS)
    return () => {
      active = false
      window.clearInterval(interval)
    }
  }, [roomCode, booth?.status])

  useEffect(() => {
    if (!cameraOpen) return

    let active = true
    const openCamera = async () => {
      setCameraState('requesting')
      setCameraMessage('Meminta izin kamera...')

      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraState('error')
        setCameraMessage('Browser ini tidak mendukung kamera. Coba gunakan Chrome, Safari, atau Edge terbaru.')
        return
      }

      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 960 } },
        })

        if (!active) {
          stream.getTracks().forEach((track) => track.stop())
          return
        }

        streamRef.current?.getTracks().forEach((track) => track.stop())
        streamRef.current = stream
        if (videoRef.current) {
          videoRef.current.srcObject = stream
          await videoRef.current.play()
        }
        setCameraState('ready')
        setCameraMessage('')
      } catch (error) {
        if (!active) return
        setCameraState('error')
        setCameraMessage(cameraErrorMessage(error))
      }
    }

    void openCamera()
    return () => {
      active = false
      streamRef.current?.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    }
  }, [cameraOpen, cameraAttempt])

  const createRoom = async () => {
    if (isSubmitting) return
    setIsSubmitting(true)
    setErrorMessage('')
    setHomeErrorLocation(null)
    setActionMessage('')
    try {
      const summary = await createBooth(selectedMode)
      rememberOwnerToken(summary.code, summary.ownerToken)
      setActiveBooth(summary, 'a')
    } catch (error) {
      setHomeErrorLocation('hero')
      setErrorMessage(error instanceof Error ? error.message : 'Room gagal dibuat. Coba lagi.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const joinRoom = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const code = roomInput.trim().toUpperCase()
    if (!code) {
      setHomeErrorLocation('join')
      setErrorMessage('Masukkan kode room terlebih dahulu.')
      return
    }

    setIsSubmitting(true)
    setErrorMessage('')
    setHomeErrorLocation(null)
    setActionMessage('')
    try {
      const summary = await getBooth(code)
      joinDialogRef.current?.close()
      setActiveBooth(summary, 'b')
    } catch (error) {
      setHomeErrorLocation('join')
      setErrorMessage(error instanceof Error ? error.message : 'Room tidak ditemukan.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const copyRoomValue = async (kind: 'code' | 'link') => {
    if (!roomCode) return
    const value = kind === 'code'
      ? roomCode
      : `${window.location.origin}/booths/${roomCode}`

    try {
      await copyToClipboard(value)
      setCopiedValue(kind)
      window.setTimeout(() => setCopiedValue(null), 2_000)
    } catch {
      setErrorMessage('Gagal menyalin. Pilih kode room lalu salin secara manual.')
    }
  }

  const canUseCamera = participant === 'a'
    ? booth?.status === 'WAITING_A'
    : booth?.status === 'WAITING_B'

  useEffect(() => {
    if (!cameraOpen || canUseCamera) return

    stopCamera()
    clearCapturedPhotos()
    setCameraOpen(false)

    if (participant === 'a' && booth?.status === 'WAITING_B') {
      setActionMessage('Empat foto kamu sudah terkirim. Sekarang tinggal menunggu Orang B.')
    } else if (booth?.status === 'READY_TO_FINALIZE') {
      setActionMessage('Foto kalian sudah lengkap. Pembuat room tinggal memilih frame.')
    } else if (booth?.status === 'COMPLETED') {
      setActionMessage('Photostrip kalian sudah siap diunduh!')
    }
  }, [booth?.status, cameraOpen, canUseCamera, participant])

  const startCamera = () => {
    if (!canUseCamera) {
      setErrorMessage('Belum giliran kamu mengambil foto. Status room akan diperbarui otomatis.')
      return
    }
    setErrorMessage('')
    setActionMessage('')
    setCameraOpen(true)
  }

  const leaveCamera = () => {
    stopCamera()
    setCameraOpen(false)
  }

  const retryCamera = () => {
    stopCamera()
    setCameraMessage('')
    setCameraAttempt((attempt) => attempt + 1)
  }

  const capturePhoto = async () => {
    if (cameraState !== 'ready' || isCapturing || capturedPhotos.length >= PHOTO_TOTAL) return

    const run = captureRunRef.current + 1
    captureRunRef.current = run
    setIsCapturing(true)
    setCameraMessage('')

    try {
      for (let number = 3; number >= 1; number -= 1) {
        if (captureRunRef.current !== run) return
        setCountdown(number)
        await new Promise((resolve) => window.setTimeout(resolve, 1_000))
      }

      if (captureRunRef.current !== run) return
      const video = videoRef.current
      if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || !video.videoWidth) {
        throw new Error('Kamera belum siap. Tunggu sebentar lalu coba lagi.')
      }

      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      const context = canvas.getContext('2d')
      if (!context) throw new Error('Foto gagal diproses. Coba lagi.')

      context.translate(canvas.width, 0)
      context.scale(-1, 1)
      context.drawImage(video, 0, 0, canvas.width, canvas.height)

      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (result) => result ? resolve(result) : reject(new Error('Foto gagal diproses. Coba lagi.')),
          'image/jpeg',
          0.88,
        )
      })

      const preview = URL.createObjectURL(blob)
      setCapturedPhotos((current) => {
        if (current.length >= PHOTO_TOTAL) {
          URL.revokeObjectURL(preview)
          return current
        }
        return [...current, { blob, preview }]
      })
    } catch (error) {
      setCameraMessage(error instanceof Error ? error.message : 'Foto gagal diambil. Coba lagi.')
    } finally {
      if (captureRunRef.current === run) {
        setCountdown(null)
        setIsCapturing(false)
      }
    }
  }

  const removeLastPhoto = () => {
    if (isCapturing || isUploading) return
    setCapturedPhotos((current) => {
      const last = current.at(-1)
      if (last) URL.revokeObjectURL(last.preview)
      return current.slice(0, -1)
    })
  }

  const uploadCapturedPhotos = async () => {
    if (!roomCode || !participant || capturedPhotos.length !== PHOTO_TOTAL || isUploading) return

    setIsUploading(true)
    setCameraMessage('Mengunggah 4 foto. Jangan tutup halaman ini...')
    try {
      const summary = await uploadPhotos(roomCode, participant, capturedPhotos.map((photo) => photo.blob))
      setBooth(summary)
      setActionMessage(participant === 'a'
        ? 'Empat foto kamu sudah terkirim. Sekarang tinggal menunggu Orang B.'
        : 'Foto kalian sudah lengkap. Pembuat room tinggal memilih frame.')
      stopCamera()
      clearCapturedPhotos()
      setCameraOpen(false)
      setErrorMessage('')
    } catch (error) {
      setCameraMessage(error instanceof Error ? error.message : 'Foto gagal diunggah. Coba lagi.')
    } finally {
      setIsUploading(false)
    }
  }

  const handleImageError = (event: SyntheticEvent<HTMLImageElement>) => {
    event.currentTarget.hidden = true
    setErrorMessage('Photostrip belum dapat dimuat. Coba muat ulang halaman beberapa saat lagi.')
  }

  const openJoinDialog = () => {
    setHomeErrorLocation('join')
    setErrorMessage('')
    const dialog = joinDialogRef.current
    if (!dialog || dialog.open) return
    dialog.showModal()
    window.setTimeout(() => document.getElementById('room-code')?.focus(), 50)
  }

  const closeJoinDialog = () => {
    joinDialogRef.current?.close()
    setErrorMessage('')
  }

  const resetHomeAndScroll = () => {
    resetToHome()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleJoinDialogBackdropClick = (event: ReactMouseEvent<HTMLDialogElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect()
    const clickedOutside = event.clientX < bounds.left
      || event.clientX > bounds.right
      || event.clientY < bounds.top
      || event.clientY > bounds.bottom

    if (clickedOutside) closeJoinDialog()
  }

  const handleFinalize = async () => {
    const token = storedOwnerToken(roomCode)
    if (!token) return
    setIsSubmitting(true)
    setErrorMessage('')
    try {
      setBooth(await finalizeBooth(roomCode, token, selectedFrame))
      setActionMessage('Frame dipasang. Photostrip kalian sudah siap!')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Photostrip gagal dibuat. Coba lagi.')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isLoadingRoom) {
    return (
      <main className="state-screen" aria-live="polite">
        <div className="state-card">
          <span className="status-spinner" aria-hidden="true" />
          <p>Membuka room...</p>
        </div>
      </main>
    )
  }

  if (roomCode && booth && participant) {
    if (cameraOpen) {
      const isReferenceCamera = participant === 'b' && booth.mode === 'REFERENCE'
      return (
        <main className="camera-screen">
          <header className="site-header camera-header">
            <button className="wordmark link-button" type="button" onClick={() => resetToHome()}>ldr / photobooth</button>
            <span className="header-note">room {roomCode} · {participant === 'a' ? 'pembuat' : 'tamu'}</span>
            <button className="text-button" type="button" onClick={leaveCamera}>keluar</button>
          </header>

          <section className="camera-content">
            <div className="camera-heading">
              <p className="eyebrow">foto {Math.min(capturedPhotos.length + 1, PHOTO_TOTAL)} dari {PHOTO_TOTAL}</p>
              <h1>Senyum<br /><em>dulu.</em></h1>
              <p>Posisikan wajah di tengah. Kamera akan menghitung mundur tiga detik.</p>
            </div>

            <div className={`camera-frame camera-frame-${cameraState} ${isReferenceCamera ? 'paired-camera-frame' : ''}`}>
              {isReferenceCamera && (
                <>
                  <img className="reference-photo" src={getReferencePhotoUrl(roomCode, Math.min(capturedPhotos.length + 1, 4))} alt="Foto pasangan di sisi kiri" />
                  <span className="camera-side-label reference-side-label">pasangan · kiri</span>
                  <span className="camera-side-label live-side-label">kamu · kanan</span>
                </>
              )}
              <video ref={videoRef} autoPlay playsInline muted aria-label="Pratinjau kamera" />
              {cameraState === 'requesting' && <div className="camera-overlay"><span className="status-spinner" /><p>Menyiapkan kamera...</p></div>}
              {cameraState === 'error' && (
                <div className="camera-overlay camera-error" role="alert">
                  <p>{cameraMessage}</p>
                  <button className="pill-button pill-button-light" type="button" onClick={retryCamera}>coba kamera lagi <span>↻</span></button>
                </div>
              )}
              {countdown !== null && <div className="countdown" aria-live="assertive">{countdown}</div>}
            </div>

            <div className="camera-controls">
              <div className="capture-slots" aria-label={`${capturedPhotos.length} dari ${PHOTO_TOTAL} foto sudah diambil`}>
                {Array.from({ length: PHOTO_TOTAL }, (_, index) => {
                  const photo = capturedPhotos[index]
                  return photo
                    ? <img key={photo.preview} src={photo.preview} alt={`Foto ${index + 1}`} />
                    : <div className="empty-photo" key={index}><span>{index + 1}</span></div>
                })}
              </div>

              <div className="camera-progress-row">
                <p><strong>{capturedPhotos.length}/{PHOTO_TOTAL}</strong> foto tersimpan di perangkat kamu.</p>
                {capturedPhotos.length > 0 && !isUploading && <button className="inline-button" type="button" onClick={removeLastPhoto}>ulang foto terakhir</button>}
              </div>

              {cameraMessage && cameraState !== 'error' && <p className={isUploading ? 'form-notice' : 'form-error'} role="status">{cameraMessage}</p>}

              {capturedPhotos.length < PHOTO_TOTAL ? (
                <button
                  className="pill-button pill-button-light capture-button"
                  type="button"
                  onClick={capturePhoto}
                  disabled={cameraState !== 'ready' || isCapturing}
                >
                  {isCapturing ? 'bersiap...' : 'ambil foto'} <span>+</span>
                </button>
              ) : (
                <button
                  className="pill-button pill-button-light capture-button"
                  type="button"
                  onClick={uploadCapturedPhotos}
                  disabled={isUploading}
                >
                  {isUploading ? 'sedang mengunggah...' : 'kirim 4 foto'} <span>→</span>
                </button>
              )}
            </div>
          </section>
        </main>
      )
    }

    if (booth.status === 'COMPLETED') {
      const resultUrl = getPhotostripUrl(roomCode, booth.resultUrl)
      const downloadUrl = getPhotostripDownloadUrl(roomCode, booth.resultUrl)
      return (
        <main className="result-screen">
          <header className="site-header result-header">
            <button className="wordmark link-button" type="button" onClick={() => resetToHome()}>ldr / photobooth</button>
            <span className="header-note">room {roomCode}</span>
            <span className="room-number">selesai</span>
          </header>

          <section className="result-content">
            <div className="result-copy">
              <p className="eyebrow">kalian berhasil</p>
              <h1>Satu jarak,<br /><em>satu cerita.</em></h1>
              <p>Empat momen dari dua tempat sudah jadi satu photostrip. Room dan seluruh fotonya otomatis terhapus 15 menit setelah hasil dibuat.</p>
              {actionMessage && <p className="success-message" role="status">{actionMessage}</p>}
              {errorMessage && <p className="form-error" role="alert">{errorMessage}</p>}
              <div className="result-actions">
                <a className="pill-button pill-button-dark" href={downloadUrl}>unduh photostrip <span>↓</span></a>
                <button className="pill-button outline-button" type="button" onClick={() => void copyRoomValue('link')}>{copiedValue === 'link' ? 'link tersalin' : 'salin link room'} <span>+</span></button>
                {participant === 'a' && storedOwnerToken(roomCode) && (
                  <button className="pill-button delete-room-button" type="button" disabled={isSubmitting} onClick={() => void handleDeleteRoom()}>
                    {isSubmitting ? 'menghapus…' : 'hapus room'}
                  </button>
                )}
              </div>
              <button className="inline-button result-home-link" type="button" onClick={() => resetToHome()}>buat room baru</button>
            </div>
            <div className="photostrip-wrap">
              <img className="photostrip" src={resultUrl} alt={`Photostrip room ${roomCode}`} onError={handleImageError} />
            </div>
          </section>
        </main>
      )
    }

    if (booth.status === 'READY_TO_FINALIZE') {
      const isOwner = participant === 'a' && Boolean(storedOwnerToken(roomCode))
      return (
        <main className="waiting-screen">
          <header className="site-header waiting-header">
            <button className="wordmark link-button" type="button" onClick={() => resetToHome()}>ldr / photobooth</button>
            <span className="header-note">room {roomCode}</span><span className="room-number">reveal</span>
          </header>
          <section className="waiting-content finalize-content">
            <p className="eyebrow">foto kalian sudah lengkap</p>
            <h1>Saatnya pilih<br /><em>suasananya.</em></h1>
            {isOwner ? <>
              <p className="waiting-copy">Pilih satu dari tiga frame. Pilihanmu akan dipakai untuk hasil akhir kalian.</p>
              <div className="frame-options">
                {(['CLASSIC', 'POLAROID', 'MIDNIGHT'] as FrameStyle[]).map((frame) => (
                  <button key={frame} type="button" className={`frame-option frame-${frame.toLowerCase()} ${selectedFrame === frame ? 'selected' : ''}`} onClick={() => setSelectedFrame(frame)}>
                    <span /><strong>{frame === 'CLASSIC' ? 'Classic' : frame === 'POLAROID' ? 'Polaroid' : 'Midnight'}</strong>
                  </button>
                ))}
              </div>
              <button className="pill-button start-button" type="button" disabled={isSubmitting} onClick={() => void handleFinalize()}>{isSubmitting ? 'membuat hasil...' : 'finalisasi photostrip'} <span>→</span></button>
            </> : <p className="waiting-copy">Pembuat room sedang memilih frame. Hasilnya akan muncul otomatis di halaman ini.</p>}
            {errorMessage && <p className="form-error" role="alert">{errorMessage}</p>}
          </section>
        </main>
      )
    }

    const isCreatorReady = participant === 'a' && booth.status === 'WAITING_A'
    const isJoinerWaiting = participant === 'b' && booth.status === 'WAITING_A'
    const isJoinerReady = participant === 'b' && booth.status === 'WAITING_B'
    const isCreatorWaiting = participant === 'a' && booth.status === 'WAITING_B'

    const heading = isCreatorReady
      ? <>Room sudah<br /><em>siap.</em></>
      : isJoinerReady
        ? <>Sekarang giliran<br /><em>kamu.</em></>
        : <>Tunggu sebentar,<br /><em>ya.</em></>

    const description = isCreatorReady
      ? 'Kamu membuat room ini. Ambil 4 foto, lalu kirim link undangannya ke pasanganmu.'
      : isJoinerWaiting
        ? 'Kamu bergabung lewat undangan. Pembuat room sedang mengambil 4 foto. Halaman ini akan berubah otomatis.'
        : isCreatorWaiting
          ? 'Empat foto kamu sudah terkirim. Bagikan link room, lalu tunggu pasanganmu menyelesaikan bagiannya.'
          : 'Empat foto dari pembuat room sudah masuk. Sekarang ambil 4 fotomu untuk menyelesaikan photostrip kalian.'

    return (
      <main className="waiting-screen">
        <header className="site-header waiting-header">
          <button className="wordmark link-button" type="button" onClick={() => resetToHome()}>ldr / photobooth</button>
          <span className="header-note">room privat · {participant === 'a' ? 'sebagai pembuat' : 'sebagai tamu'}</span>
          <span className="room-number">{roomCode}</span>
        </header>

        <section className="waiting-content">
          <p className="eyebrow">room {roomCode}</p>
          <h1>{heading}</h1>
          <p className="waiting-copy">{description}</p>

          <div className="room-code-display" aria-label={`Kode room ${roomCode}`}>{roomCode}</div>
          <div className="room-actions">
            <button className="pill-button outline-button" type="button" onClick={() => void copyRoomValue('code')}>
              {copiedValue === 'code' ? 'kode tersalin' : 'salin kode'} <span>+</span>
            </button>
            <button className="pill-button outline-button" type="button" onClick={() => void copyRoomValue('link')}>
              {copiedValue === 'link' ? 'link tersalin' : 'salin link undangan'} <span>↗</span>
            </button>
            {participant === 'a' && storedOwnerToken(roomCode) && (
              <button className="pill-button delete-room-button" type="button" disabled={isSubmitting} onClick={() => void handleDeleteRoom()}>
                {isSubmitting ? 'menghapus…' : 'hapus room'}
              </button>
            )}
          </div>

          {(isCreatorReady || isJoinerReady) && (
            <button className="pill-button start-button" type="button" onClick={startCamera}>mulai ambil 4 foto <span>→</span></button>
          )}

          {actionMessage && <p className="success-message" role="status">{actionMessage}</p>}
          {errorMessage && <p className="form-error" role="alert">{errorMessage}</p>}
          <p className="waiting-status" aria-live="polite">
            <span className={`status-dot ${canUseCamera ? 'status-dot-ready' : ''}`} />
            {canUseCamera ? 'siap mengambil foto' : 'menunggu pembaruan dari pasangan'}
          </p>
          {pollMessage && <p className="poll-message" role="status">{pollMessage}</p>}
        </section>
      </main>
    )
  }

  return (
    <main className="home-page">
      <header className="site-header home-header">
        <button className="wordmark link-button" type="button" onClick={resetHomeAndScroll}>ldr / photobooth</button>
        <span className="header-note">bareng, walau berjauhan</span>
        <nav aria-label="Navigasi utama">
          <a href="#cara-main">cara main</a>
          <button type="button" onClick={openJoinDialog}>gabung room</button>
        </nav>
      </header>

      <section className="home-hero" aria-labelledby="hero-title">
        <div className="home-glow home-glow-one" aria-hidden="true" />
        <div className="home-glow home-glow-two" aria-hidden="true" />
        <div className="home-hero-inner">
          <div className="home-hero-copy">
            <p className="eyebrow">photobooth berdua dari tempat berbeda</p>
            <h1 id="hero-title">Dua tempat.<br /><em>Satu photostrip.</em></h1>
            <p className="hero-copy">Buat room, ambil 4 foto bergantian, lalu gabungkan jadi satu photostrip—bareng pasangan, sahabat, atau siapa pun yang lagi jauh.</p>

            <fieldset className="home-mode-picker">
              <legend>Pilih suasana sesi</legend>
              <div className="mode-segments">
                <label className={selectedMode === 'REFERENCE' ? 'selected' : ''}>
                  <input type="radio" name="booth-mode" value="REFERENCE" checked={selectedMode === 'REFERENCE'} onChange={() => setSelectedMode('REFERENCE')} />
                  <span>Reference</span><small>pose nyambung</small>
                </label>
                <label className={selectedMode === 'SURPRISE' ? 'selected' : ''}>
                  <input type="radio" name="booth-mode" value="SURPRISE" checked={selectedMode === 'SURPRISE'} onChange={() => setSelectedMode('SURPRISE')} />
                  <span>Surprise</span><small>reveal bareng</small>
                </label>
              </div>
              <p aria-live="polite">{selectedMode === 'REFERENCE'
                ? 'Tamu melihat foto pembuat sebagai referensi agar pose keduanya bisa saling merespons.'
                : 'Foto pembuat disembunyikan dari tamu dan baru terlihat bersama saat reveal.'}</p>
            </fieldset>

            <div className="home-hero-actions">
              <button className="home-primary-action" type="button" onClick={createRoom} disabled={isSubmitting}>
                <span>{isSubmitting ? 'membuat room...' : 'buat room baru'}</span><b aria-hidden="true">↗</b>
              </button>
              <button className="home-secondary-action" type="button" onClick={openJoinDialog}>
                sudah punya kode? <span>gabung room</span>
              </button>
            </div>
            {errorMessage && homeErrorLocation === 'hero' && <p className="hero-error form-error" role="alert">{errorMessage}</p>}
          </div>

          <figure className={`product-preview ${selectedMode === 'SURPRISE' ? 'is-surprise' : ''}`} role="img" aria-label={`Contoh photostrip dalam ${selectedMode === 'REFERENCE' ? 'Reference' : 'Surprise'} Mode`}>
            <div className="preview-note preview-note-a">A / pembuat</div>
            <div className="preview-note preview-note-b">B / tamu</div>
            <div className="sample-tape" aria-hidden="true" />
            <div className="sample-strip">
              <div className="sample-strip-head"><span>ldr / booth</span><span>08·25·26</span></div>
              {[0, 1, 2, 3].map((index) => (
                <div className="sample-row" key={index}>
                  <div className={`sample-photo sample-person-a sample-palette-${index}`}><span className="portrait-head" /><span className="portrait-body" /></div>
                  <div className={`sample-photo sample-person-b sample-palette-${index}`}><span className="portrait-head" /><span className="portrait-body" /><span className="surprise-cover"><b>?</b><small>lihat saat reveal</small></span></div>
                </div>
              ))}
              <div className="sample-caption">4 foto + 4 foto → 1 strip</div>
            </div>
            <div className="preview-badge"><strong>{selectedMode === 'REFERENCE' ? 'reference' : 'surprise'}</strong><span>mode</span></div>
          </figure>
        </div>
      </section>

      <div className="home-trust-row" aria-label="Informasi singkat">
        <span>tanpa akun</span><span>4 foto per orang</span><span>room privat</span><span>hapus otomatis</span>
      </div>

      <section className="how-section" id="cara-main" aria-labelledby="how-title">
        <div className="how-heading">
          <p className="section-label">cara main / tiga langkah</p>
          <h2 id="how-title">Bikin kenangan<br /><em>tanpa harus ketemu.</em></h2>
        </div>
        <ol className="how-steps">
          <li><span>01</span><div><strong>Buat & bagikan</strong><p>Pilih mode, buat room privat, lalu kirim link ke teman fotomu.</p></div></li>
          <li><span>02</span><div><strong>Foto bergantian</strong><p>Kalian mengambil empat foto masing-masing, dari kamera dan tempat sendiri.</p></div></li>
          <li><span>03</span><div><strong>Reveal & simpan</strong><p>Pilih frame setelah reveal, lalu unduh photostrip sebelum room terhapus otomatis.</p></div></li>
        </ol>
        <div className="mode-explainer">
          <article><span aria-hidden="true">R</span><div><strong>Reference Mode</strong><p>Tamu melihat foto pembuat supaya gestur dan ceritanya bisa saling merespons.</p></div></article>
          <article><span aria-hidden="true">S</span><div><strong>Surprise Mode</strong><p>Tamu berfoto tanpa mengintip, lalu keduanya melihat hasil lengkap saat reveal.</p></div></article>
        </div>
      </section>

      <section className="home-closing" aria-label="Tentang privasi room">
        <p className="eyebrow">room privat untuk dua orang</p>
        <p>Hasil tersimpan selama 15 menit untuk diunduh. Setelah itu, room dan seluruh foto dihapus otomatis.</p>
        <span aria-hidden="true">15′</span>
      </section>

      <dialog
        className="join-dialog"
        id="gabung"
        ref={joinDialogRef}
        aria-labelledby="join-title"
        onClick={handleJoinDialogBackdropClick}
      >
        <button className="dialog-close" type="button" onClick={closeJoinDialog} aria-label="Tutup form gabung">×</button>
        <p className="eyebrow">sudah dapat undangan?</p>
        <h2 id="join-title">Masuk ke<br /><em>room ini.</em></h2>
        <form className="join-form" onSubmit={joinRoom} aria-busy={isSubmitting}>
          <label htmlFor="room-code">kode room</label>
          <div className="dialog-form-row">
            <input
              id="room-code"
              name="room-code"
              value={roomInput}
              onChange={(event) => setRoomInput(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6))}
              placeholder="K7F2AD"
              maxLength={6}
              autoComplete="off"
              spellCheck={false}
              aria-describedby={`join-hint${errorMessage && homeErrorLocation === 'join' ? ' join-error' : ''}`}
              aria-invalid={Boolean(errorMessage && homeErrorLocation === 'join')}
            />
            <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'mengecek...' : 'masuk'} <span aria-hidden="true">→</span></button>
          </div>
          <p id="join-hint" className="form-hint">Enam karakter dari pembuat room. Kamu akan masuk sebagai tamu.</p>
          {errorMessage && homeErrorLocation === 'join' && <p id="join-error" className="form-error" role="alert">{errorMessage}</p>}
        </form>
      </dialog>

      <footer className="home-footer"><span>ldr / photobooth</span><span>dibuat untuk yang sedang berjauhan</span><span>2026</span></footer>
    </main>
  )
}

export default App
