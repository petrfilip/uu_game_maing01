import UU5 from "uu5g04";
import Config from "../../config/config.js";

const TAG = Config.TAG + "Core.";

export default {
  ...Config,

  TAG,
  Css: UU5.Common.Css.createCssModule(
    TAG.replace(/\.$/, "")
      .toLowerCase()
      .replace(/\./g, "-")
      .replace(/[^a-z-]/g, ""),
    process.env.NAME + "/" + process.env.OUTPUT_NAME + "@" + process.env.VERSION // this helps preserve proper order of styles among loaded libraries
  ),
};
