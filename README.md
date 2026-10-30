# MeshDrop — bug and gap audit

Repository: [irealashu/MeshDrop](<https://github.com/irealashu/MeshDrop?utm_source=chatgpt.com>)

First, an important correction to my previous analysis: I have now verified the exact repository. It is a small Android project generated from Google's AI Studio repository template, with only 2 commits visible on its main branch. The earlier analysis described several implementation details as confirmed findings without sufficient source verification. Those details should not be treated as a completed code audit.

I want to avoid inventing bugs or attributing code to files that might not exist.

The repository metadata confirms:

- Android/Gradle project.
- Kotlin Compose plugin.
- KSP plugin.
- Secrets Gradle plugin.
- An `.env.example` file.
- A `tmp/` directory.
- Project description: Zero-Router P2P Android Utility & ADB/File Bridge.
  irealashu/MeshDrop: MeshDrop · GitHub
  +2

I can identify the architectural risks and missing engineering requirements now. For exact line-level bugs, I need the actual source files.

## 1\. Critical gaps and potential bugs

The following is a verification-oriented audit: these are issues to investigate and requirements to enforce, not claims that every issue has already been found in the source.

### A. Networking and peer discovery

Critical priority| ID | Potential bug / gap | Consequence | Recommended fix |
| --- | --- | --- | --- |
| N01 | No guaranteed Wi-Fi Aware hardware support | App cannot connect on unsupported phones | Check hardware capability before starting |
| N02 | Incorrect service-name matching | Devices cannot discover each other | Use identical discovery service identifiers |
| N03 | Discovery session lifecycle not managed | Stale peers and failed reconnections | Explicit attach, publish, subscribe and close states |
| N04 | No discovery timeout | UI may remain searching indefinitely | Add configurable timeout and retry |
| N05 | No peer expiration | Disconnected devices remain visible | Track last-seen timestamps |
| N06 | Hardcoded remote IP addresses | Connection fails on different network configurations | Resolve endpoints from the established network |
| N07 | No connection retry strategy | Temporary network failures terminate transfers | Exponential backoff with bounded retries |
| N08 | No duplicate peer filtering | Same phone may appear multiple times | Maintain stable peer identities |
| N09 | No simultaneous connection policy | Conflicting connection attempts | Introduce connection arbitration |
| N10 | No explicit network teardown | Resources and sessions leak | Close sockets, callbacks and sessions |
| N11 | No network-switch handling | Transfer may break when connectivity changes | Observe network availability and loss |
| N12 | No compatibility fallback | Devices without Wi-Fi Aware cannot participate | Add a clearly defined alternative transport |

Suggested fix: Create a dedicated connection state machine.

mermaid

```

stateDiagram-v2
    [*] --> Idle
    Idle --> Discovering
    Discovering --> PeerFound
    PeerFound --> Pairing
    Pairing --> Connecting
    Connecting --> Connected
    Connected --> Transferring
    Transferring --> Connected
    Connecting --> Failed
    Pairing --> Failed
    Transferring --> Failed
    Failed --> Discovering
    Connected --> Disconnecting
    Disconnecting --> Idle
```

Every transition should be driven by actual network events, not UI assumptions.

### B. Security and authentication

Critical priority| ID | Potential bug / gap | Consequence | Recommended fix |
| --- | --- | --- | --- |
| S01 | Hardcoded encryption secrets | Anyone extracting the APK can recover them | Use authenticated ephemeral key exchange |
| S02 | PIN generated from predictable identifiers | PIN can be guessed or reproduced | Generate SAS from cryptographic session transcript |
| S03 | PIN confirmation not enforced on both devices | Unauthorized pairing | Require mutual confirmation |
| S04 | No identity binding | Attacker can impersonate a peer | Bind public keys to verified identities |
| S05 | No replay protection | Old messages may be resent | Add session IDs and sequence numbers |
| S06 | No message authentication | Payload manipulation | Authenticate all protocol frames |
| S07 | No key rotation | Long-lived compromised sessions remain exposed | Use fresh session keys |
| S08 | No secure key persistence policy | Secrets may leak or become unrecoverable | Use Android Keystore appropriately |
| S09 | No rate limiting | Pairing and connection abuse | Limit attempts and introduce cooldowns |
| S10 | No security protocol versioning | Future clients may negotiate insecure modes | Version and validate the handshake |

Recommended cryptographic design:

- X25519 for ephemeral key agreement.
- HKDF-SHA256 for session key derivation.
- AES-256-GCM or ChaCha20-Poly1305 for authenticated encryption.
- Cryptographically random nonces.
- Mutual authentication through a verified short authentication string.
- Separate keys for each transmission direction.

Never implement your own cryptographic primitives.

### C. File-transfer reliability

Critical priority| ID | Potential bug / gap | Consequence | Recommended fix |
| --- | --- | --- | --- |
| F01 | Whole-file buffering | Out-of-memory crashes | Stream data in bounded chunks |
| F02 | Fixed file-size limit | Large videos cannot transfer | Configurable size limits and streaming |
| F03 | No receiver acknowledgement validation | False success messages | Wait for receiver confirmation |
| F04 | No resume support | Restart from zero after interruption | Persist transfer ID and received offset |
| F05 | No chunk sequence validation | Missing or duplicated data | Sequence numbers and acknowledgements |
| F06 | No integrity verification | Corrupted files may be accepted | SHA-256 verification |
| F07 | No duplicate filename policy | Existing files may be overwritten | Safe unique filename generation |
| F08 | Untrusted filenames | Path traversal or invalid writes | Sanitize and normalize filenames |
| F09 | No storage quota | Device storage exhaustion | Check available disk space |
| F10 | No cancellation protocol | Orphaned transfers | Explicit cancel messages |
| F11 | No transfer timeout | Stuck transfers | Per-operation deadlines |
| F12 | No concurrent transfer limits | Memory and CPU exhaustion | Bounded transfer queue |
| F13 | No partial-file cleanup | Storage pollution | Temporary-file lifecycle management |
| F14 | No MIME validation | Incorrect file handling | Detect and validate file types |

Recommended transfer architecture:

text

```

FILE_OFFER
    |
    v
RECEIVER_ACCEPT / RECEIVER_REJECT
    |
    v
TRANSFER_START
    |
    v
CHUNK 0 ---- ACK 0
CHUNK 1 ---- ACK 1
CHUNK 2 ---- ACK 2
    |
    v
TRANSFER_COMPLETE
    |
    v
VERIFY SHA-256
    |
    v
FINAL_ACK
    |
    v
ATOMIC FILE COMMIT
```

This protocol should be specified before implementing advanced transfer features.

## 2\. Android-specific gaps

High priority| ID | Potential issue | Recommended fix |
| --- | --- | --- |
| A01 | Missing runtime permission handling | Implement Android-version-specific permission checks |
| A02 | Missing nearby-device permission explanations | Explain why each permission is needed |
| A03 | Foreground service lifecycle not guaranteed | Handle service creation, destruction and restart |
| A04 | Background execution restrictions | Test Android 12–16 background behavior |
| A05 | Battery optimization | Avoid unnecessary background wakeups |
| A06 | Screen rotation may reset UI state | Use ViewModel-backed state |
| A07 | Activity recreation may duplicate connections | Make connection lifecycle independent of Activity |
| A08 | Service crashes may lose transfer state | Persist transfer metadata |
| A09 | Unsupported hardware may crash initialization | Add capability detection |
| A10 | Android file URI handling | Use ContentResolver and persistable URI permissions where appropriate |
| A11 | Storage permission assumptions | Use scoped storage and Storage Access Framework |
| A12 | APK installation handling | Use PackageInstaller and explicit user consent |

One particularly important issue is separating the lifetime of the UI from the lifetime of an active transfer.

Closing the app screen should not accidentally destroy an ongoing file transfer.

## 3\. ADB and device-bridge security

This deserves special attention because the repository describes itself as an ADB/File Bridge utility.

ADB is significantly more sensitive than ordinary file sharing.

| ID | Risk | Recommended fix |
| --- | --- | --- |
| D01 | Exposed ADB service | Never expose an unrestricted ADB endpoint |
| D02 | Unauthorized command execution | Require explicit device authorization |
| D03 | Shell command injection | Never construct shell commands using untrusted input |
| D04 | Excessive device privileges | Apply least-privilege access |
| D05 | No command auditing | Record security-relevant operations |
| D06 | Remote access without authentication | Require authenticated sessions |
| D07 | Dangerous file operations | Require confirmation for destructive operations |
| D08 | No ADB session timeout | Automatically expire inactive sessions |

My recommendation: Keep ADB functionality separate from file transfer.

The file-sharing component should not automatically inherit the permissions of an ADB bridge.

## 4\. Build system and dependency management

The root Gradle file uses a version catalog and several plugins, including KSP and a secrets plugin.

GitHub

These are the areas I would audit:

| ID | Potential gap | Fix |
| --- | --- | --- |
| B01 | Missing dependency version constraints | Pin compatible dependency versions |
| B02 | Outdated dependencies | Run dependency vulnerability checks |
| B03 | Secrets accidentally committed | Add secret scanning to CI |
| B04 | Incorrect environment configuration | Validate required configuration at build time |
| B05 | Debug configuration leaking into release | Separate debug and release configurations |
| B06 | Missing ProGuard/R8 rules | Verify release builds |
| B07 | No reproducible build process | Pin Gradle wrapper and JDK versions |
| B08 | No CI pipeline | Build and test on every pull request |
| B09 | No release signing safeguards | Secure signing credentials |
| B10 | Unnecessary temporary files | Review and clean `tmp/` before release |

The `.env.example` file and secrets plugin deserve particular attention. A sample configuration must never contain real API keys or production credentials.

## 5\. Architecture and code quality

Medium–high priorityI recommend investigating these architectural gaps:

| ID | Gap | Improvement |
| --- | --- | --- |
| C01 | UI coupled to networking | Use repository interfaces |
| C02 | Business logic in Activity | Move into ViewModels/use cases |
| C03 | No central error model | Define typed application errors |
| C04 | Unstructured connection state | Introduce state machines |
| C05 | No dependency injection strategy | Use Hilt or another DI framework |
| C06 | No protocol abstraction | Separate transport from transfer protocol |
| C07 | No persistent transfer repository | Store transfer metadata |
| C08 | No logging strategy | Use structured logging |
| C09 | No configuration management | Centralize network and transfer settings |
| C10 | No protocol version negotiation | Add compatible client-version handling |
| C11 | No observability | Add transfer and connection diagnostics |
| C12 | No modular testing strategy | Introduce unit and integration test boundaries |

A clean architecture will become especially important when supporting multiple transport methods.

For example, the same file-transfer logic should eventually work over Wi-Fi Aware, Wi-Fi Direct or a local hotspot without rewriting the entire application.

## 6\. Missing features that matter to users

These are product gaps rather than confirmed software defects.

| Feature | Priority | Why it matters |
| --- | --- | --- |
| QR-based pairing | High | Easy device identification |
| Device trust history | High | Avoid repeated pairing |
| Large-file streaming | Critical | Reliable video and APK transfers |
| Transfer resume | High | Avoid restarting large transfers |
| Batch file transfer | High | Better user experience |
| Folder transfer | Medium | Practical file sharing |
| Transfer history | Medium | Track previous transfers |
| Transfer cancellation | High | User control |
| Receive confirmation | High | Prevent unwanted transfers |
| Storage management | High | Prevent filling device storage |
| Network diagnostics | High | Troubleshoot failed connections |
| Multi-device transfer | Medium | Support groups |
| Accessibility | Medium | Support all users |

## 7\. Testing gaps

I would not release MeshDrop without these tests.

| Test | Expected result |
| --- | --- |
| Two supported Android phones | Discover each other |
| Unsupported Wi-Fi Aware device | Graceful error |
| Wi-Fi disabled | Clear recovery instructions |
| Internet disabled | Local transfer works |
| 1 KB file | Successful transfer |
| 100 MB file | Successful transfer |
| 1 GB file | No memory exhaustion |
| Duplicate filenames | No accidental overwrite |
| Disconnect during transfer | Recoverable failure |
| App backgrounded | Transfer behaves correctly |
| Receiver storage full | Safe failure |
| Malformed network packet | Connection rejected |
| Incorrect PIN | Connection rejected |
| Replay an old packet | Packet rejected |
| Unauthorized peer | Transfer rejected |
| Rapid connect/disconnect | No resource leaks |

### Device compatibility matrix

Test at least:

- Google Pixel.
- Samsung Galaxy.
- OnePlus.
- Xiaomi/Redmi.
- A device without Wi-Fi Aware.
- Different Android versions.

Do not assume that two devices running the same Android version have identical Wi-Fi Aware hardware capabilities.

## 8\. Recommended order for fixing everything

I would organize the work into five development milestones.

## MeshDrop repair roadmap

1. Milestone 1 — Make connectivity work
   P0- Correct peer discovery.
   - Fix endpoint resolution.
   - Handle network lifecycle.
   - Implement connection state management.
   Deliverable: Two real phones connect reliably.
2. Milestone 2 — Secure the connection
   P0- Implement authenticated key exchange.
   - Implement mutual PIN verification.
   - Protect protocol messages.
   - Add replay protection.
   Deliverable: Authenticated encrypted sessions.
3. Milestone 3 — Fix file transfers
   P1- Streaming architecture.
   - Chunk acknowledgements.
   - File integrity checks.
   - Resume and cancellation.
   - Safe file handling.
   Deliverable: Reliable large-file transfer.
4. Milestone 4 — Android reliability
   P1- Foreground service lifecycle.
   - Permission handling.
   - Background transfer recovery.
   - Storage management.
   Deliverable: Stable operation on real devices.
5. Milestone 5 — Production preparation
   P2- Automated testing.
   - Dependency auditing.
   - CI/CD.
   - Performance testing.
   - Documentation and release preparation.
   Deliverable: Release candidate.
