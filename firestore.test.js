const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "gen-lang-client-0038135719";
const DATABASE_ID = "ai-studio-android-crashint-6818dd3b-7fac-4ddf-bced-4d984d0e5441";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

test("unauthenticated users cannot read anything", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthedDb.collection("users").get());
});

test("users can read their own profile", async () => {
  const aliceId = "alice";
  const aliceDb = testEnv.authenticatedContext(aliceId).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("users").doc(aliceId).get());
});

test("users cannot read other users profiles", async () => {
  const aliceId = "alice";
  const bobId = "bob";
  const aliceDb = testEnv.authenticatedContext(aliceId).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(bobId).get());
});

test("users can create their own profile with valid data", async () => {
  const aliceId = "alice";
  const aliceDb = testEnv.authenticatedContext(aliceId).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("users").doc(aliceId).set({
    userId: aliceId,
    email: "alice@example.com",
    createdAt: new Date()
  }));
});

test("users cannot create profile with mismatched userId", async () => {
  const aliceId = "alice";
  const bobId = "bob";
  const aliceDb = testEnv.authenticatedContext(aliceId).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(aliceId).set({
    userId: bobId,
    email: "alice@example.com",
    createdAt: new Date()
  }));
});
