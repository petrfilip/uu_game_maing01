//@@viewOn:imports
import {createVisualComponent, useEffect, useRef} from "uu5g04-hooks";
import Config from "./config/config";

//@@viewOff:imports

const Canvas = createVisualComponent({
  //@@viewOn:statics
  displayName: Config.TAG + "Canvas",
  //@@viewOff:statics


  //@@viewOn:propTypes
  //@@viewOff:propTypes

  //@@viewOn:defaultProps
  //@@viewOff:defaultProps

  render(props) {
    //@@viewOff:hooks

    const {draw, ...rest} = props
    const canvasRef = useRef()

    useEffect(() => {

      const canvas = canvasRef.current
      const context = canvas.getContext('2d')
      let frameCount = 0
      let animationFrameId

      const render = () => {
        frameCount++
        draw(context, frameCount)
        animationFrameId = window.requestAnimationFrame(render)
      }
      render()

      return () => {
        window.cancelAnimationFrame(animationFrameId)
      }
    }, [draw])
    //@@viewOff:hooks


    //@@viewOn:render
    return <canvas width={500} height={500} style={{
      border: "1px solid black",
      height: "100vh",
      width: "100vw"
    }} ref={canvasRef} {...rest} />
    //@@viewOff:render
  },
});

export default Canvas;
