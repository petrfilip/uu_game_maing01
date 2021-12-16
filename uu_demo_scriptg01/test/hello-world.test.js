const { TestHelper } = require("uu_script_devkitg01");

describe("hello", () => {
  test("HDS", async () => {
    const session = await TestHelper.login();

    const result = await TestHelper.runScript("pfilip-chart.js", {}, session);
    expect(result.isError).toEqual(false);
  });
});
