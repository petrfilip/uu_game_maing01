import {createComponent} from "uu5g04-hooks";


const RenderHelper = createComponent({


  render() {
    //@@viewOn:hooks

// Options
    let ctx = null;
    let particlesPerExplosion = 20;
    let particlesMinSpeed = 3;
    let particlesMaxSpeed = 20;
    let particlesMinSize = 1;
    let particlesMaxSize = 10;
    let explosions = [];
    let fps = 60;
    let now, delta;
    let then = 0;  // Zero start time
    let interval = 1000 / fps;

// Draw
// as time is passed you need to start with requestAnimationFrame
    requestAnimationFrame(draw);

    function draw(time) {  //requestAnimationFrame frame passes the time
      requestAnimationFrame(draw);
      delta = time - then;
      if (delta > interval) {
        then = time
        drawExplosion();
      }
    }

// Draw explosion(s)
    function drawExplosion() {
      if (explosions.length === 0) {
        return;
      }
      for (let i = 0; i < explosions.length; i++) {
        let explosion = explosions[i];
        let particles = explosion.particles;
        if (particles.length == 0) {
          explosions.splice(i, 1);
          //return;
          continue;
        }
        for (let ii = 0; ii < particles.length; ii++) {
          let particle = particles[ii];
          // Check particle size
          // If 0, remove
          if (particle.size < 0) {
            particles.splice(ii, 1);
            // return;
            continue;
          }
          ctx.beginPath();
          ctx.arc(particle.x, particle.y, particle.size, Math.PI * 2, 0, false);
          ctx.closePath();
          ctx.fillStyle = 'rgb(' + particle.r + ',' + particle.g + ',' + particle.b + ')';
          ctx.fillStyle = "blue" // todo obstacle.type
          ctx.fill();
          // Update
          particle.x += particle.xv;
          particle.y += particle.yv;
          particle.size -= .1;
        }
      }
    }

    function addExplosion(context, x, y) {
      ctx = context
      explosions.push(new explosion(x, y));
    }

// Explosion
    function explosion(x, y) {
      this.particles = [];
      for (let i = 0; i < particlesPerExplosion; i++) {
        this.particles.push(new particle(x, y));
      }
    }

// Particle
    function particle(x, y) {
      this.x = x;
      this.y = y;
      this.xv = randInt(particlesMinSpeed, particlesMaxSpeed, false);
      this.yv = randInt(particlesMinSpeed, particlesMaxSpeed, false);
      this.size = randInt(particlesMinSize, particlesMaxSize, true);
      this.r = randInt(0, 222);
      this.g = '00';
      this.b = randInt(0, 255);
    }

// Returns an random integer, positive or negative
// between the given value
    function randInt(min, max, positive) {
      let num
      if (positive === false) {
        num = Math.floor(Math.random() * max) - min;
        num *= Math.floor(Math.random() * 2) == 1 ? 1 : -1;
      } else {
        num = Math.floor(Math.random() * max) + min;
      }
      return num;
    }

    //@@viewOff:hooks

    //@@viewOn:render
    return ({addExplosion});
    //@@viewOff:render
  },


});

export default RenderHelper;
