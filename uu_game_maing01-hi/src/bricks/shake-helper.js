import {createComponent} from "uu5g04-hooks";


const ShakeHelper = createComponent({


  render() {
    //@@viewOn:hooks
    let shakingElements = [];

    let shake = function (element, magnitude = 16, angular = false) {
      //First set the initial tilt angle to the right (+1)
      let tiltAngle = 1;

      //A counter to count the number of shakes
      let counter = 1;

      //The total number of shakes (there will be 1 shake per frame)
      let numberOfShakes = 15;

      //Capture the element's position and angle so you can
      //restore them after the shaking has finished
      let startX = 0,
        startY = 0,
        startAngle = 0;

      // Divide the magnitude into 10 units so that you can
      // reduce the amount of shake by 10 percent each frame
      let magnitudeUnit = magnitude / numberOfShakes;

      //The `randomInt` helper function
      let randomInt = (min, max) => {
        return Math.floor(Math.random() * (max - min + 1)) + min;
      };

      //Add the element to the `shakingElements` array if it
      //isn't already there
      if(shakingElements.indexOf(element) === -1) {
        //console.log("added")
        shakingElements.push(element);

        //Add an `updateShake` method to the element.
        //The `updateShake` method will be called each frame
        //in the game loop. The shake effect type can be either
        //up and down (x/y shaking) or angular (rotational shaking).
        if(angular) {
          angularShake();
        } else {
          upAndDownShake();
        }
      }

      //The `upAndDownShake` function
      function upAndDownShake() {

        //Shake the element while the `counter` is less than
        //the `numberOfShakes`
        if (counter < numberOfShakes) {

          //Reset the element's position at the start of each shake
          element.style.transform = 'translate(' + startX + 'px, ' + startY + 'px)';

          //Reduce the magnitude
          magnitude -= magnitudeUnit;

          //Randomly change the element's position
          let randomX = randomInt(-magnitude, magnitude);
          let randomY = randomInt(-magnitude, magnitude);

          element.style.transform = 'translate(' + randomX + 'px, ' + randomY + 'px)';

          //Add 1 to the counter
          counter += 1;

          requestAnimationFrame(upAndDownShake);
        }

        //When the shaking is finished, restore the element to its original
        //position and remove it from the `shakingElements` array
        if (counter >= numberOfShakes) {
          element.style.transform = 'translate(' + startX + ', ' + startY + ')';
          shakingElements.splice(shakingElements.indexOf(element), 1);
        }
      }

      //The `angularShake` function
      function angularShake() {
        if (counter < numberOfShakes) {
          console.log(tiltAngle);
          //Reset the element's rotation
          element.style.transform = 'rotate(' + startAngle + 'deg)';

          //Reduce the magnitude
          magnitude -= magnitudeUnit;

          //Rotate the element left or right, depending on the direction,
          //by an amount in radians that matches the magnitude
          let angle = Number(magnitude * tiltAngle).toFixed(2);
          console.log(angle);
          element.style.transform = 'rotate(' + angle + 'deg)';
          counter += 1;

          //Reverse the tilt angle so that the element is tilted
          //in the opposite direction for the next shake
          tiltAngle *= -1;

          requestAnimationFrame(angularShake);
        }

        //When the shaking is finished, reset the element's angle and
        //remove it from the `shakingElements` array
        if (counter >= numberOfShakes) {
          element.style.transform = 'rotate(' + startAngle + 'deg)';
          shakingElements.splice(shakingElements.indexOf(element), 1);
          //console.log("removed")
        }
      }

    };


    //@@viewOff:hooks

    //@@viewOn:render
    return ({shake});
    //@@viewOff:render
  },


});

export default ShakeHelper;
