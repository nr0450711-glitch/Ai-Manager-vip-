const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vip_123";
const BOB_UID = "bob_vip_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any Jarvis VIP collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Hack task",
      category: "TASK",
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Authenticated owner: UserProfile create, partial update, and immutable field protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Nasim",
      preferredLanguage: "BILINGUAL",
      voicePitch: 1.08,
      voiceRate: 1.0,
      wakeWordEnabled: false,
      automationConfirmed: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "HINDI",
      wakeWordEnabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Spoof",
      preferredLanguage: "ENGLISH",
      voicePitch: 1.0,
      voiceRate: 1.0,
      wakeWordEnabled: false,
      automationConfirmed: false,
      createdAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated owner: AssistantTask, UserMemory, ChatMessage, and SocialDraft parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Task create (no @DocumentId `id` field, optional notes omitted)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Morning briefing",
      category: "REMINDER",
      dueLabel: "08:00 AM",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on task
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List tasks scoped to user
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );

  // Reject extra `id` field in create payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_2").set({
      id: "task_2",
      userId: ALICE_UID,
      title: "Invalid ID field",
      category: "TASK",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Memory create & update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      memoryKey: "Home City",
      memoryValue: "New Delhi",
      category: "PREFERENCE",
      approved: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Chat message create (updates disallowed)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "USER",
      content: "Hey JARVIS, Aaj ka mausam batao",
      languageTag: "bi",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").update({
      content: "Tampered history",
    })
  );

  // SocialDraft lifecycle: DRAFT -> SHARED, then locked against further edits
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("socialDrafts").doc("draft_1").set({
      userId: ALICE_UID,
      platform: "FACEBOOK",
      caption: "Building JARVIS VIP on Galaxy A51!",
      hasPhoto: true,
      status: "DRAFT",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("socialDrafts").doc("draft_1").update({
      status: "SHARED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("socialDrafts").doc("draft_1").update({
      status: "DRAFT",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const ts = new Date(Date.now() - 5000);
    await db.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").set({
      userId: ALICE_UID,
      title: "Private task",
      category: "TASK",
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("alice_task").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
