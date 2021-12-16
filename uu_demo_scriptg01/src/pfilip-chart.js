const {console, session} = scriptContext;
const {AppClient} = require("uu_appg01_server");

async function main() {
  const identity = session.getIdentity();
  const userName = identity.getName();

  console.info(`Hello ${userName}`);

  const dockitUri = "https://uuapp.plus4u.net/uu-dockit-maing02/27b8573056014000b13a195a13f48f8f";
  const dockitClient = new AppClient({baseUri: dockitUri, session});

  const myAppUri = "https://uuapp.plus4u.net/tga-trainingcd-maing01/4a92776e7367402db0e90f8b6e4fb1be";
  const myAppClient = new AppClient({ baseUri: myAppUri, session });
  const calculateListResult = await myAppClient.get("calculate/list");


  const loadDtoIn = {
    id: "61bbaf2d729dfe0027751f0b",
    documentId: "61b20571378ec60027f2563e",
  };
  let loadDtoOut = await dockitClient.get("document/sheet/load", loadDtoIn);

  console.info(`Sheet data loaded`);


  const chart = `<uu5string/>
    <UU5.Bricks.Section header="${"Calculate result"}">
      <UU5.SimpleChart.BarChart colorSchema="green" displayTooltip=false displayCartesianGrid=true
       series='<uu5json/>[
        {
          "valueKey": "value1",
          "name": "Series 1",
          "colorSchema": "red-rich"
        },
        {
          "valueKey": "value2",
          "name": "Series 2",
          "colorSchema": "blue-rich"
        },
        {
          "valueKey": "value3",
          "name": "Series 3",
          "colorSchema": "lime-rich"

        }
      ]'>
        <uu5string.pre>
          <uu5json/>
                    ${JSON.stringify(
    calculateListResult.itemList.map((item) => {
      return {
        label: item.key,
        value1: item.valueList[0],
        value2: item.valueList[1],
        value3: item.valueList[2],
      };
    })
  )}
        </uu5string.pre>
      </UU5.SimpleChart.BarChart>
    </UU5.Bricks.Section>
  `;

  await dockitClient.post("document/sheet/section/update", {
    sys: {
      rev: loadDtoOut.sectionList[0].sys.rev,
    },
    sheetId: "61bbaf2d729dfe0027751f0b",
    documentId: "61b20571378ec60027f2563e",
    code: loadDtoOut.sectionList[0].code,
    content: chart,
    editable: false,
  });

  console.info(`Chart to docking pushed`);


  return {uuAppErrorMap: {}};

  return {
    uuAppErrorMap: {}
  };
}

main();
