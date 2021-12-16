// imports
const { Validator } = require("uu_appg01_server").Validation;
const { ValidationHelper } = require("uu_appg01_server").AppServer;
const { UseCaseError } = require("uu_appg01_server").AppServer;
const AppClient = require("uu_appg01_server").AppClient;

// constants
const DEFAULT_RATING = 5;

// dtoInSchema
const dtoInSchema = `
  const rateJokesDtoInSchemaType = shape({
    baseUri: uri().isRequired(),
    rating: number()
  });
`;

// errors
const Errors = {
  ERROR_PREFIX: "uu-jokes-maing01/script/rate-jokes/",

  InvalidDtoIn: class extends UseCaseError {
    constructor(dtoOut, paramMap) {
      super({ dtoOut, paramMap, status: 400 });
      this.message = `DtoIn is not valid.`;
      this.code = `${Errors.ERROR_PREFIX}invalidDtoIn`;
    }
  },

  JokeListFailed: class extends UseCaseError {
    constructor(dtoOut, paramMap, cause) {
      super({ dtoOut, paramMap, status: 400 }, cause);
      this.message = `Jokes loading failed.`;
      this.code = `${Errors.ERROR_PREFIX}jokeListFailed`;
    }
  },

  JokeAddRatingFailed: class extends UseCaseError {
    constructor(dtoOut, paramMap, cause) {
      super({ dtoOut, paramMap, status: 400 }, cause);
      this.message = `Rating of joke with id ${paramMap.id} failed.`;
      this.code = `${Errors.ERROR_PREFIX}jokeAddRatingFailed`;
    }
  }
};

// helpers
async function addWarning(code, message, params = {}) {
  let warning = {};
  warning.type = "warning";
  warning.code = `${Errors.ERROR_PREFIX}${code}`;
  warning.message = message;
  dtoOut.uuAppErrorMap[code] = warning;
  console.warning(`${code}: ${message}`);
}

function validateDtoIn(dtoInSchema) {
  const validator = new Validator(dtoInSchema);
  const validationResult = validator.validate("rateJokesDtoInSchemaType", dtoIn);

  // A1, A2
  return ValidationHelper.processValidationResult(dtoIn, validationResult, `${Errors.ERROR_PREFIX}unsupportedKeys`, Errors.InvalidDtoIn);
}

async function getJokes() {
  let jokeListDtoOut;

  try {
    jokeListDtoOut = await jokesClient.get("joke/list", {});
  } catch (e) {
    // A3, A6
    throw new Errors.JokeListFailed(dtoOut, { baseUri: dtoIn.baseUri }, e);
  }

  return jokeListDtoOut.itemList;
}

async function rateJokes(jokes) {
  let remaining = jokes.length;

  for (const joke of jokes) {
    if (--remaining % 10 === 0) {
      console.info(`Remaining ${remaining} jokes...`);
    }

    await rateJoke(joke);
  }
}

async function rateJoke(joke) {
  const identity = session.getIdentity();
  if (joke.uuIdentity === identity.getUuIdentity()) {
    // A4
    await addWarning("identityAndAuthorAreSame", `Joke with name "${joke.name}" can't be rated because script is running under joke author ${identity.getName()}`);
    return;
  }

  let jokeAddRatingDtoIn = {
    id: joke.id,
    rating: dtoIn.rating
  };

  try {
    await jokesClient.post("joke/addRating", jokeAddRatingDtoIn);
  } catch (e) {
    // A5
    throw new Errors.JokeAddRatingFailed(dtoOut, { id: joke.id }, e);
  }
}

function calcRatingStats(jokes) {
  let ratingStats = jokes.reduce((total, joke) => {
    let rating = Math.floor(joke.averageRating);

    let ratingStat = total.find(s => s.rating === rating);

    if (!ratingStat) {
      ratingStat = { rating, count: 0 };
      total.push(ratingStat);
    }

    ratingStat.count++;
    return total;
  }, []);

  return ratingStats.sort((s1, s2) => s1.rating - s2.rating);
}

function renderRatingStats(ratingStats, title) {
  return `<uu5string/>
    <UU5.Bricks.Section header="${title}">
      <UU5.SimpleChart.BarChart colorSchema="green" displayTooltip=false displayCartesianGrid=true>
        <uu5string.pre>
          <uu5json/>
          ${JSON.stringify(
    ratingStats.map(stat => {
      return {
        label: stat.rating,
        value: stat.count
      };
    })
  )}
        </uu5string.pre>
      </UU5.SimpleChart.BarChart>
    </UU5.Bricks.Section>
  `;
}

const { dtoIn, console, session } = scriptContext;
const dtoOut = { dtoIn };
let jokesClient;

async function main() {
  // HDS 1
  console.info("HDS1 - Validating dtoIn...");
  dtoOut.uuAppErrorMap = validateDtoIn(dtoInSchema);
  dtoIn.rating = dtoIn.rating || DEFAULT_RATING;

  // HDS 2
  console.info("HDS2 - Getting jokes...");
  jokesClient = new AppClient({ baseUri: dtoIn.baseUri, session });
  const jokes = await getJokes();

  // HDS 3
  const ratingStatsBefore = calcRatingStats(jokes);
  dtoOut.ratingStatsBefore = ratingStatsBefore;
  console.info(renderRatingStats(ratingStatsBefore, "Rating - before script execution"));

  // HDS 4
  console.info(`HDS3 - Rate ${jokes.length} jokes...`);
  await rateJokes(jokes);

  // HDS 5
  const newJokes = await getJokes();

  // HDS 6
  const ratingStatsAfter = calcRatingStats(newJokes);
  dtoOut.ratingStatsAfter = ratingStatsAfter;
  console.info(renderRatingStats(ratingStatsAfter, "Rating - after script execution"));

  // HDS 7
  console.info("HDS4 - Finished.");
  return dtoOut;
}

main();
