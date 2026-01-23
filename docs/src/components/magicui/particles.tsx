'use client'

import React, { useEffect, useRef, useState } from 'react'

interface MousePosition {
  x: number
  y: number
}

function MousePosition(): MousePosition {
  const [mousePosition, setMousePosition] = useState<MousePosition>({
    x: 0,
    y: 0,
  })

  useEffect(() => {
    const handleMouseMove = (event: MouseEvent) => {
      setMousePosition({ x: event.clientX, y: event.clientY })
    }

    window.addEventListener('mousemove', handleMouseMove)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
    }
  }, [])

  return mousePosition
}

interface ParticlesProps {
  className?: string
  quantity?: number
  staticity?: number
  ease?: number
  size?: number
  refresh?: boolean
  color?: string
  vx?: number
  vy?: number
}

function hexToRgb(hex: string): number[] {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result
    ? [parseInt(result[1], 16), parseInt(result[2], 16), parseInt(result[3], 16)]
    : [255, 255, 255]
}

const Particles: React.FC<ParticlesProps> = ({
  className = '',
  quantity = 100,
  staticity = 50,
  ease = 50,
  size = 0.4,
  refresh = false,
  color = '#ffffff',
  vx = 0,
  vy = 0,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const canvasContainerRef = useRef<HTMLDivElement>(null)
  const context = useRef<CanvasRenderingContext2D | null>(null)
  const circles = useRef<any[]>([])
  const mousePosition = MousePosition()
  const mouse = useRef<{ x: number; y: number }>({ x: 0, y: 0 })
  const canvasSize = useRef<{ w: number; h: number }>({ w: 0, h: 0 })
  const dpr = typeof window !== 'undefined' ? window.devicePixelRatio : 1

  useEffect(() => {
    if (canvasRef.current) {
      context.current = canvasRef.current.getContext('2d')
    }
    const resizeCanvas = () => {
      if (canvasContainerRef.current && canvasRef.current) {
        const w = canvasContainerRef.current.offsetWidth
        const h = canvasContainerRef.current.offsetHeight
        canvasSize.current = { w, h }
        canvasRef.current.width = w * dpr
        canvasRef.current.height = h * dpr
        if (context.current) {
          context.current.scale(dpr, dpr)
        }
      }
    }
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
    return () => window.removeEventListener('resize', resizeCanvas)
  }, [dpr])

  useEffect(() => {
    mouse.current = mousePosition
  }, [mousePosition])

  const rgb = hexToRgb(color)

  const drawCircle = (circle: any, update = false) => {
    if (context.current) {
      const { x, y, translateX, translateY, size: circleSize, alpha } = circle
      context.current.translate(translateX, translateY)
      context.current.beginPath()
      context.current.arc(x, y, circleSize, 0, 2 * Math.PI)
      context.current.fillStyle = `rgba(${rgb.join(', ')}, ${alpha})`
      context.current.fill()
      context.current.setTransform(dpr, 0, 0, dpr, 0, 0)

      if (!update) {
        circles.current.push(circle)
      }
    }
  }

  const clearContext = () => {
    if (context.current) {
      context.current.clearRect(0, 0, canvasSize.current.w, canvasSize.current.h)
    }
  }

  const drawParticles = () => {
    clearContext()
    const particleCount = quantity
    for (let i = 0; i < particleCount; i++) {
      const angle = (i / particleCount) * 2 * Math.PI
      const distance = 50 + Math.sin(i) * 50
      const x = mouse.current.x + distance * Math.cos(angle)
      const y = mouse.current.y + distance * Math.sin(angle)

      const circle = {
        x: x,
        y: y,
        translateX: x,
        translateY: y,
        size: size + Math.random() * size,
        alpha: Math.random() * 0.5 + 0.3,
      }

      drawCircle(circle, false)
    }

    circles.current.forEach((circle, index) => {
      circle.alpha -= 0.01
      if (circle.alpha <= 0) {
        circles.current.splice(index, 1)
      } else {
        drawCircle(circle, true)
      }
    })
  }

  useEffect(() => {
    const animationFrameId = setInterval(drawParticles, 1000 / 60)
    return () => clearInterval(animationFrameId)
  }, [quantity, size])

  return (
    <div ref={canvasContainerRef} className={className}>
      <canvas
        ref={canvasRef}
        style={{
          display: 'block',
          width: '100%',
          height: '100%',
        }}
      />
    </div>
  )
}

export default Particles
