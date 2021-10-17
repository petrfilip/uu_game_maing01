import { mount, shallow } from "enzyme";
import WelcomeRow from "../../src/bricks/welcome-row.js";

describe("WelcomeRow", function () {
  test("should render props", function () {
    let icon = "mdi-check";
    let text = "asdfegsdf";
    let wrapper = mount(
      <WelcomeRow id="id" icon={icon}>
        {text}
      </WelcomeRow>
    );

    let renderedHtml = wrapper.html();
    expect(renderedHtml).toContain(icon);
    expect(renderedHtml).toContain(text);
  });

  test("should match snapshot", function () {
    let icon = "mdi-check";
    let wrapper = shallow(<WelcomeRow id="id" icon={icon} />);
    expect(wrapper).toMatchSnapshot();
  });
});
