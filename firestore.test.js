const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Anon",
      role: "user",
      planTier: "starter",
      messageCount: 0,
      imageCount: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Owner can create and update UserProfile without role escalation or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      planTier: "starter",
      messageCount: 0,
      imageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Allowed partial update: planTier, lastActiveConversationId, messageCount, updatedAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      planTier: "pro",
      lastActiveConversationId: "conv_1",
      messageCount: 3,
      updatedAt: pastDate(1000),
    })
  );

  // Reject self-escalation to admin
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: pastDate(500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject non-admin list of /users
  await assertFails(aliceDb.collection("users").get());

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      messageCount: 4,
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Owner can create, list, update, and delete workspace subcollections matching Kotlin payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // Folder
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("folder_1").set({
      userId: ALICE_UID,
      name: "Architecture",
      colorKey: "cobalt",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").get()
  );

  // Conversation (no @DocumentId `id` field, uses `pinned` not `isPinned`)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Kotlin Coroutines Review",
      folderId: "folder_1",
      pinned: true,
      modelId: "gemini-3.8-flash",
      personaId: "preset_assistant",
      lastMessagePreview: "Let's check structured concurrency...",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject conversation with `@DocumentId` `"id"` key
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_2").set({
      id: "conv_2",
      userId: ALICE_UID,
      title: "Bad Doc",
      pinned: false,
      modelId: "gemini-3.8-flash",
      personaId: "preset_assistant",
      lastMessagePreview: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // ChatMessage
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        role: "model",
        content: "Here is the diff.",
        attachments: ["spec.pdf (1.2 MB)"],
        reaction: "liked",
        followUps: ["Explain Flow cancellation"],
        partialTimeout: false,
        sequenceIndex: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Persona, MemoryEntry, BookmarkSnippet
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("personas").doc("p_1").set({
      userId: ALICE_UID,
      name: "Reviewer",
      tagline: "Strict code reviewer",
      systemInstruction: "Audit edge cases concisely.",
      builtIn: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("m_1").set({
      userId: ALICE_UID,
      category: "Preference",
      content: "Prefer Jetpack Compose M3 examples.",
      enabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("b_1").set({
      userId: ALICE_UID,
      kind: "snippet",
      title: "StateFlow Unwrapper",
      content: "generateSequence(error) { it.cause }",
      language: "kotlin",
      sourceConversationId: "conv_1",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation blocks reading or mutating another user's workspace", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bobDb = ctx.firestore();
    const created = pastDate(10000);
    await bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      displayName: "Bob",
      role: "user",
      planTier: "starter",
      messageCount: 1,
      imageCount: 0,
      createdAt: created,
      updatedAt: created,
    });
    await bobDb.collection("users").doc(BOB_UID).collection("memories").doc("m_bob").set({
      userId: BOB_UID,
      category: "Bio & Role",
      content: "Bob's secret project",
      enabled: true,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("memories").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("memories").doc("m_bob").delete()
  );
});

test("Role-gated Admin Console blocks unverified email spoofing and grants verified admins", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const created = pastDate(10000);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "user",
      planTier: "starter",
      messageCount: 5,
      imageCount: 2,
      createdAt: created,
      updatedAt: created,
    });
  });

  // Spoofed unverified admin email must be rejected
  const spoofAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "u4115518@gmail.com",
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofAdminDb.collection("users").get());

  // Verified bootstrapped admin succeeds
  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "u4115518@gmail.com",
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(realAdminDb.collection("users").get());
  await assertSucceeds(
    realAdminDb.collection("users").doc(ALICE_UID).update({
      planTier: "team",
      role: "admin",
      updatedAt: pastDate(500),
    })
  );
});
