import Calls from "../src/calls.js";
import HttpClient from "./http-client.js";

const appAssetsBaseUri = (
  document.baseURI ||
  (document.querySelector("base") || {}).href ||
  location.protocol + "//" + location.host + location.pathname
).replace(/^(.*)\/.*$/, "$1/"); // strip what's after last slash

/**
 * Mocks
 */
Calls.call = (method, url, dtoIn) => {
  let mockUrl = (process.env.MOCK_DATA_BASE_URI || appAssetsBaseUri) + "mock/data/" + url + ".json";
  let responsePromise = HttpClient.get(mockUrl);
  return dtoIn != null ? responsePromise.then(dtoIn.done, dtoIn.fail) : responsePromise;
};

Calls.getCommandUri = (aUseCase) => {
  return aUseCase;
};

export default Calls;
