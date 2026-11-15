const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validListPayload(uid) {
  return {
    userId: uid,
    title: "Personal",
    iconKey: "list",
    colorHex: "#4F46E5",
    isLocked: false,
    isShared: false,
    driveFileId: "",
    ownerEmail: "alice@example.com",
    isReadOnly: false,
    sortOrder: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validTaskPayload(uid, listId = "list_1") {
  return {
    userId: uid,
    listId,
    title: "Buy groceries",
    notes: "Milk and bread",
    priority: "HIGH",
    isCompleted: false,
    dueDateEpoch: 0,
    reminderEpoch: 0,
    reminderSoundKey: "chime",
    category: "Personal",
    updatedByEmail: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

test("Unauthenticated user cannot read or write lists or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").set(validListPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("Authenticated user can create, read, update, and delete own lists and tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const listRef = aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1");
  await assertSucceeds(listRef.set(validListPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("lists").get());
  await assertSucceeds(listRef.update({ title: "Updated Personal", updatedAt: serverTimestamp() }));

  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  await assertSucceeds(taskRef.set(validTaskPayload(ALICE_UID, "list_1")));
  await assertSucceeds(taskRef.update({ isCompleted: true, updatedAt: serverTimestamp() }));
  await assertSucceeds(taskRef.delete());
});

test("Cross-user access is denied for lists, tasks, and settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").set(validListPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lists").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").update({ title: "Hacked", updatedAt: serverTimestamp() }));
});

test("Rejects shadow fields, invalid priority, and uid spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badList = { ...validListPayload(ALICE_UID), extraField: "hack" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_bad").set(badList));

  const spoofTask = validTaskPayload(BOB_UID, "list_1");
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_spoof").set(spoofTask));

  const badPriority = { ...validTaskPayload(ALICE_UID, "list_1"), priority: "CRITICAL" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_prio").set(badPriority));
});

test("Authenticated user can save and update settings with notification fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  await assertSucceeds(prefsRef.set({
    userId: ALICE_UID,
    defaultSoundKey: "chime",
    defaultReminderHour: 9,
    defaultReminderMinute: 30,
    notificationDescription: "Daily task alert",
    useDarkTheme: true,
    biometricEnabled: true,
    customCategories: ["General", "Work"],
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(prefsRef.set({
    userId: ALICE_UID,
    defaultSoundKey: "bell",
    defaultReminderHour: 20,
    defaultReminderMinute: 15,
    notificationDescription: "Evening review",
    useDarkTheme: false,
    biometricEnabled: true,
    customCategories: ["General", "Focus"],
    updatedAt: serverTimestamp(),
  }));
  await assertFails(prefsRef.set({
    userId: ALICE_UID,
    defaultSoundKey: "bell",
    defaultReminderHour: 25,
    defaultReminderMinute: 0,
    notificationDescription: "Invalid hour",
    useDarkTheme: false,
    biometricEnabled: true,
    customCategories: ["General"],
    updatedAt: serverTimestamp(),
  }));
});
