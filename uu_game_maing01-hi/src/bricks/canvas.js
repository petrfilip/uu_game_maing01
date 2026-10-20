//@@viewOn:imports
import UU5 from "uu5g04";
import {createVisualComponent, useEffect, useRef} from "uu5g04-hooks";
import Config from "./config/config";

//@@viewOff:imports

const Canvas = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Canvas",
  //@@viewOff:statics


  //@@viewOn:propTypes
  propTypes: {
    draw: UU5.PropTypes.func.isRequired,
    width: UU5.PropTypes.number,
    height: UU5.PropTypes.number,
    canvasRef: UU5.PropTypes.object,
  },
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  defaultProps: {
    width: 1400,
    height: 600,
    canvasRef: null,
  },
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOn:hooks
    const {draw, width, height, canvasRef: forwardedRef, ...rest} = props

    const canvasRef = useRef()

    // The parent re-renders on every server tick, so `draw` is a different function ~20x per second.
    // Holding it in a ref instead of listing it as an effect dependency is what lets the animation
    // loop below live for the whole lifetime of the canvas - it used to be cancelled and restarted
    // on every tick, which also reset `frameCount` back to 1 each time.
    const drawRef = useRef(draw)
    drawRef.current = draw

    useEffect(() => {
      const canvas = canvasRef.current
      const context = canvas.getContext('2d')
      let animationFrameId

      // The parent needs the canvas element itself (and not the wrapping div) to translate mouse
      // coordinates into playfield coordinates.
      if (forwardedRef) {
        forwardedRef.current = canvas
      }

      const render = () => {
        animationFrameId = window.requestAnimationFrame(render)
        drawRef.current(context, canvas)
      }
      animationFrameId = window.requestAnimationFrame(render)

      return () => {
        window.cancelAnimationFrame(animationFrameId)
        if (forwardedRef) {
          forwardedRef.current = null
        }
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
    //@@viewOff:hooks


    //@@viewOn:render
    return (
      <div style={{border: "3px solid red", lineHeight: 0}}>
        {/* The bitmap is the playfield in full, CSS scales it down to whatever width the card has,
            so the whole arena stays visible however wide the playfield is configured. */}
        <canvas
          width={width}
          height={height}
          style={{width: "100%", height: "auto", display: "block"}}
          ref={canvasRef}
          {...rest}
        />
      </div>
    )
    //@@viewOff:render
  },
});

export default Canvas;
