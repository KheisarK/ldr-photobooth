import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

function App() {
  const [roomCode, setRoomCode] = useState('')
  const [roomInput, setRoomInput] = useState('')
  const [cameraStarted, setCameraStarted] = useState(false)
  const [countdown, setCountdown] = useState<number | null>(null)
  const [snapshot, setSnapshot] = useState<string | null>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)

  const createRoom = () => {
    setRoomCode(`LDR-${Math.floor(100 + Math.random() * 900)}`)
  }

  const joinRoom = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const code = roomInput.trim().toUpperCase()
    if (code) setRoomCode(code)
  }

  useEffect(() => {
    if (!cameraStarted) return

    navigator.mediaDevices.getUserMedia({ video: true, audio: false })
      .then((stream) => {
        streamRef.current = stream
        if (videoRef.current) videoRef.current.srcObject = stream
      })
      .catch(() => setCameraStarted(false))

    return () => streamRef.current?.getTracks().forEach((track) => track.stop())
  }, [cameraStarted])

  const capturePhoto = () => {
    if (countdown !== null) return
    setCountdown(3)
    const timer = window.setInterval(() => {
      setCountdown((current) => {
        if (current === null || current <= 1) {
          window.clearInterval(timer)
          const video = videoRef.current
          if (video) {
            const canvas = document.createElement('canvas')
            canvas.width = video.videoWidth || 640
            canvas.height = video.videoHeight || 480
            canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height)
            setSnapshot(canvas.toDataURL('image/jpeg'))
          }
          return null
        }
        return current - 1
      })
    }, 1000)
  }

  if (roomCode) {
    if (cameraStarted) {
      return (
        <main className="camera-screen">
          <header className="site-header camera-header"><a className="wordmark" href="/" onClick={() => setRoomCode('')}>ldr / photobooth</a><span className="header-note">room {roomCode}</span><button className="text-button" type="button" onClick={() => setCameraStarted(false)}>exit</button></header>
          <section className="camera-content">
            <div className="camera-heading"><p className="eyebrow">room {roomCode}</p><h1>Look at<br /><em>each other.</em></h1></div>
            <div className="camera-frame">
              {snapshot ? <img src={snapshot} alt="Your captured photobooth snapshot" /> : <video ref={videoRef} autoPlay playsInline muted />}
              {countdown !== null && <div className="countdown">{countdown}</div>}
            </div>
            <div className="camera-controls"><p>{snapshot ? 'Your moment is ready.' : 'When you are both ready, capture together.'}</p><button className="pill-button pill-button-light" type="button" onClick={snapshot ? () => setSnapshot(null) : capturePhoto}>{snapshot ? 'retake photo' : 'capture photo'} <span>{snapshot ? '↺' : '+'}</span></button></div>
          </section>
        </main>
      )
    }

    return (
      <main className="waiting-screen">
        <header className="site-header waiting-header">
          <a className="wordmark" href="/" onClick={() => setRoomCode('')}>ldr / photobooth</a>
          <span className="header-note">private room</span>
          <span className="room-number">{roomCode}</span>
        </header>
        <section className="waiting-content">
          <p className="eyebrow">room {roomCode}</p>
          <h1>Waiting for<br /><em>your person.</em></h1>
          <p className="waiting-copy">Share this code with your partner. When they arrive, your photobooth will be ready.</p>
          <div className="room-code-display" aria-label={`Room code ${roomCode}`}>{roomCode}</div>
          <button className="pill-button pill-button-dark" type="button" onClick={() => navigator.clipboard?.writeText(roomCode)}>copy room code <span>+</span></button>
          <button className="pill-button start-button" type="button" onClick={() => setCameraStarted(true)}>start photobooth <span>-&gt;</span></button>
          <p className="waiting-status"><span className="status-dot" /> waiting for partner to join</p>
        </section>
      </main>
    )
  }

  return (
    <main>
      <header className="site-header">
        <a className="wordmark" href="/">ldr / photobooth</a>
        <span className="header-note">together, remotely</span>
        <nav aria-label="Main navigation">
          <a href="#how-it-works">how it works</a>
          <a href="#join">join a room</a>
        </nav>
      </header>

      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-wash" aria-hidden="true" />
        <div className="hero-content">
          <p className="eyebrow">a shared moment, from anywhere</p>
          <h1 id="hero-title">Distance,<br /><em>framed.</em></h1>
          <p className="hero-copy">A quiet little photobooth for two people who are not in the same room.</p>
          <div className="hero-actions">
            <button className="pill-button pill-button-light" type="button" onClick={createRoom}>create a room <span>+</span></button>
            <a className="pill-button pill-button-ghost" href="#join">join with a code <span>-&gt;</span></a>
          </div>
        </div>
        <p className="scroll-note">scroll to begin / 01</p>
      </section>

      <section className="intro-section" id="how-it-works">
        <div className="section-label">01 / the ritual</div>
        <div className="intro-copy">
          <h2>Make a memory<br /><span>in real time.</span></h2>
          <p>You bring the smile. We bring the timer, the frame, and a little bit of magic across the miles.</p>
        </div>
        <div className="steps" aria-label="How it works">
          <div className="step"><span>01</span><strong>Make a room</strong><p>Start a private space for two.</p></div>
          <div className="step"><span>02</span><strong>Share the code</strong><p>Invite your favourite person.</p></div>
          <div className="step"><span>03</span><strong>Take the shot</strong><p>Press capture at the same time.</p></div>
        </div>
      </section>

      <section className="join-section" id="join">
        <div className="join-panel">
          <p className="eyebrow">already invited?</p>
          <h2>Enter the<br />room.</h2>
          <form className="join-form" onSubmit={joinRoom}>
            <label htmlFor="room-code">room code</label>
            <div className="form-row"><input id="room-code" name="room-code" value={roomInput} onChange={(event) => setRoomInput(event.target.value)} placeholder="e.g. LDR-204" /><button className="pill-button pill-button-dark" type="submit">enter room <span>-&gt;</span></button></div>
          </form>
        </div>
        <p className="join-aside">No account.<br />No awkward setup.<br />Just you two.</p>
      </section>

      <footer><span>ldr / photobooth</span><span>made for the in-between moments</span><span>2026</span></footer>
    </main>
  )
}

export default App
