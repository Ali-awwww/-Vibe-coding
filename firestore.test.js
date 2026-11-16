const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-vibecodi-e4471e48-df5a-44fd-adfd-c9cc33662178";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

let testEnv;

before(async () => {
  const rules = fs.readFileSync("firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("unauthenticated users cannot read or write any documents", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthedDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthedDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthedDb.collectionGroup("crashes").get());
});

test("users can create and update their own profile with valid timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      email: "alice+updated@example.com",
      displayName: "Alice Dev",
      updatedAt: new Date(),
    })
  );
});

test("users cannot read another user profile or spoof userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );
});

test("owner can create, update allowed fields on project, and delete project; cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const nowMs = Date.now();

  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(
    projectRef.set({
      id: "proj_1",
      name: "Vibe Crash Monitor",
      description: "Production telemetry",
      ownerId: ALICE_UID,
      dataRetentionDays: 90,
      piiScrubbingEnabled: true,
      createdAt: nowMs,
    })
  );

  await assertSucceeds(
    projectRef.update({
      dataRetentionDays: 30,
      piiScrubbingEnabled: false,
    })
  );

  // Immutable ownerId change must fail
  await assertFails(projectRef.update({ ownerId: BOB_UID }));

  // Cross-user read and write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").delete());
});

test("crashes, issues, applications, and collectionGroup queries enforce owner/user filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const nowMs = Date.now();

  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");
  await assertSucceeds(
    projRef.set({
      id: "proj_1",
      name: "App Suite",
      description: "",
      ownerId: ALICE_UID,
      dataRetentionDays: 90,
      piiScrubbingEnabled: true,
      createdAt: nowMs,
    })
  );

  await assertSucceeds(
    projRef.collection("applications").doc("app_1").set({
      id: "app_1",
      projectId: "proj_1",
      name: "Android Client",
      platform: "Android",
      packageName: "com.example.client",
      ownerId: ALICE_UID,
      createdAt: nowMs,
    })
  );

  await assertSucceeds(
    projRef.collection("crashes").doc("crash_1").set({
      id: "crash_1",
      applicationId: "app_1",
      projectId: "proj_1",
      exceptionType: "NullPointerException",
      message: "Attempt to invoke virtual method",
      stackTrace: "MainActivity.kt:42",
      timestamp: nowMs,
      isResolved: false,
      userId: ALICE_UID,
    })
  );

  await assertSucceeds(
    projRef.collection("issues").doc("issue_1").set({
      id: "issue_1",
      projectId: "proj_1",
      applicationId: "app_1",
      ownerId: ALICE_UID,
      title: "NullPointerException",
      fingerprint: "fp_abc123",
      status: "NEW",
      firstSeen: nowMs,
      lastOccurrence: nowMs,
    })
  );

  // Scoped collectionGroup queries succeed for owner
  await assertSucceeds(aliceDb.collectionGroup("applications").where("ownerId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collectionGroup("crashes").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collectionGroup("issues").where("ownerId", "==", ALICE_UID).get());

  // Unfiltered or cross-user collectionGroup queries fail
  await assertFails(aliceDb.collectionGroup("crashes").get());
  await assertFails(bobDb.collectionGroup("crashes").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collectionGroup("issues").where("ownerId", "==", ALICE_UID).get());
});

test("notifications, audit_logs, model_teams, team_executions, and team_usage_records validate schema and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const nowMs = Date.now();

  const notifRef = aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1");
  await assertSucceeds(
    notifRef.set({
      id: "notif_1",
      projectId: "proj_1",
      title: "Critical Crash",
      message: "NPE detected",
      isRead: false,
      timestamp: nowMs,
    })
  );
  await assertSucceeds(notifRef.update({ isRead: true }));
  await assertFails(notifRef.update({ title: "Tampered" }));

  const auditRef = aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1");
  await assertSucceeds(
    auditRef.set({
      id: "log_1",
      projectId: "proj_1",
      userId: ALICE_UID,
      action: "PROJECT_CREATED",
      details: "Created project",
      timestamp: nowMs,
    })
  );
  // Audit logs are immutable
  await assertFails(auditRef.update({ action: "TAMPERED" }));
  await assertFails(auditRef.delete());

  const teamRef = aliceDb.collection("users").doc(ALICE_UID).collection("model_teams").doc("team_1");
  await assertSucceeds(
    teamRef.set({
      id: "team_1",
      name: "Diagnostics Team",
      ownerId: ALICE_UID,
      isPublic: false,
      isTemplate: false,
      createdAt: nowMs,
      updatedAt: nowMs,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("team_executions").doc("exec_1").set({
      id: "exec_1",
      teamId: "team_1",
      userId: ALICE_UID,
      prompt: "Analyze crash",
      status: "COMPLETED",
      startedAt: nowMs,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("team_usage_records").doc("usage_1").set({
      id: "usage_1",
      userId: ALICE_UID,
      teamId: "team_1",
      modelId: "firebase_ai::gemini-3.8-flash",
      tokensInput: 100,
      tokensOutput: 50,
      totalTokens: 150,
      costUsd: 0,
      timestamp: nowMs,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("model_teams").doc("team_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").get());
});
