const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const ADMIN_UID = "admin_uid_999";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated caller is denied on users, projects, and auditLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("projects").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("auditLogs").get());
});

test("Authenticated owner can create and read own profile & private PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      publicId: "LUM-7K9P2M4X",
      username: "alice_dev",
      isPublic: true,
      vipStatus: "free",
      isBanned: false,
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      provider: "google.com",
      createdAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user PII & profile access is denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      publicId: "LUM-7K9P2M4X",
      username: "alice_dev",
      isPublic: true,
      vipStatus: "free",
      isBanned: false,
      createdAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      createdAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("memories").doc("mem1").set({
      userId: ALICE_UID,
      category: "fact",
      title: "Language",
      content: "Prefers Hinglish",
      isPinned: true,
      createdAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
});

test("User cannot self-escalate vipStatus or inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      publicId: "LUM-7K9P2M4X",
      username: "alice_dev",
      isPublic: true,
      vipStatus: "free",
      isBanned: false,
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      vipStatus: "lifetime",
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: now,
    })
  );
});

test("Projects enforce owner query alignment and admin moderation lock", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").set({
      userId: ALICE_UID,
      ownerPublicId: "LUM-7K9P2M4X",
      name: "Nova Shop",
      projectType: "website",
      framework: "html5",
      buildStatus: "ready",
      deploymentStatus: "not_deployed",
      isModerated: false,
      createdAt: now,
    })
  );

  // Unfiltered list must fail for regular users
  await assertFails(aliceDb.collection("projects").get());
  // Scoped list must succeed
  await assertSucceeds(aliceDb.collection("projects").where("userId", "==", ALICE_UID).get());

  // Cross-user read must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("projects").doc("proj_1").get());

  // Verified admin email can moderate project and write append-only audit log
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "pranavsmmsupport@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("projects").doc("proj_1").update({
      isModerated: true,
      deploymentStatus: "blocked",
      moderationReason: "Policy review",
      updatedAt: now,
    })
  );

  // Alice cannot un-moderate her own project
  await assertFails(
    aliceDb.collection("projects").doc("proj_1").update({
      isModerated: false,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("auditLogs").doc("log_1").set({
      actorUid: ADMIN_UID,
      action: "BLOCK_PROJECT",
      targetUid: ALICE_UID,
      targetId: "proj_1",
      details: "Blocked deployment",
      createdAt: now,
    })
  );
  // Audit logs are append-only even for admins
  await assertFails(adminDb.collection("auditLogs").doc("log_1").delete());
});
