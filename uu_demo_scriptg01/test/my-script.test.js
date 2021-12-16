const { TestHelper } = require("uu_script_devkitg01");

describe("MyScript", () => {
  test("HDS", async () => {
    const session = await TestHelper.login();

    const dtoIn = {};

    const result = await TestHelper.runScript("my-script.js", dtoIn, session);
    expect(result.isError).toEqual(false);
  });
});
