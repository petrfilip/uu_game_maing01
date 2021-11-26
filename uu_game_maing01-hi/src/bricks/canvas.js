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

    const visibleMap = {
      width: 1000,
      height: 750,
    };

    const mapSize = {
      width: 1200,
      height: 900,
    };



    useEffect(() => {

      const canvas = canvasRef.current
      const context = canvas.getContext('2d')
      let frameCount = 0
      let animationFrameId

      const render = () => {
        animationFrameId = window.requestAnimationFrame(render)
        frameCount++
        draw(context, frameCount, canvas)
      }
      render()

      return () => {
        window.cancelAnimationFrame(animationFrameId)
      }
    }, [draw])
    //@@viewOff:hooks


    //@@viewOn:render
    return <div style={{ border:"3px solid red",  overflow: "hidden", width: visibleMap.width, height: visibleMap.height}}><canvas width={mapSize.width} height={mapSize.height} ref={canvasRef} {...rest} /></div>
    //@@viewOff:render
  },
});

export default Canvas;
