// This file was auto-generated according to the "namespace" setting in package.json.
// Manual changes to this file are discouraged, if values are inconsistent with package.json setting.
import UU5 from "uu5g04";

const TAG = "UuGame.";

export default {
  TAG,
  Css: UU5.Common.Css.createCssModule(
    TAG.replace(/\.$/, "")
      .toLowerCase()
      .replace(/\./g, "-")
      .replace(/[^a-z-]/g, ""),
    process.env.NAME + "/" + process.env.OUTPUT_NAME + "@" + process.env.VERSION // this helps preserve proper order of styles among loaded libraries
  ),
};
