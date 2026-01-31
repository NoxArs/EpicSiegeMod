# Epic Siege Mod (1.7.10) – AI Enhanced Edition

Epic Siege Mod is a **Minecraft 1.7.10 Forge mod** focused on **significantly enhancing hostile mob AI and combat behavior**, making survival more tactical, reactive, and punishing.

This repository is a **derived and actively modified version** of Epic Siege Mod, adapted and extended for **modern GTNH-style modpack environments** and custom AI experimentation.

---

## ✨ Features

Epic Siege Mod enhances hostile mobs with advanced behaviors, including but not limited to:

- Smarter target selection and threat evaluation
- Coordinated mob behaviors (group logic, role-based actions)
- Environmental interaction (pillaring, digging, demolition, navigation)
- Enhanced combat tactics for zombies, skeletons, creepers, blazes, endermen, and more
- Configurable difficulty scaling and dimension-based rules

The goal is **not raw stat inflation**, but **AI-driven difficulty**.

---

## 📦 Minecraft & Modloader

- **Minecraft:** 1.7.10  
- **Modloader:** Minecraft Forge (1.7.10)  
- **Target Environment:** Hardcore / expert modpacks (e.g. GTNH-style packs)

---

## 🧱 Project Structure

This project uses **GTNH's modernized Gradle + Forge 1.7.10 project layout**, based on:

- **Project skeleton:**  
  https://github.com/GTNewHorizons/ExampleMod1.7.10

The build system, formatting rules, and CI layout follow GTNH conventions.

---

## 🔗 Upstream & Credits

This project is **not an original implementation** of Epic Siege Mod.

It is derived from and heavily based on:

- **Epic Siege Mod (Reign Modpack Edition)**  
  https://github.com/HostileNetworks/Epic-Siege-Mod_ReignModpack

Original concept and core implementation by **funwayguy** and contributors.

This repository contains:
- Refactors and cleanups
- AI behavior fixes and rebalancing
- Additional AI logic and edge-case handling
- Build system modernization for GTNH-style environments

All original credit belongs to the upstream authors.

---

## 📜 License

This project follows the license of the upstream Epic Siege Mod.

See the `LICENSE` file for details.

---

## ⚠️ Disclaimer

This mod is designed for **expert-level gameplay** and may be **extremely punishing**.

It is **not recommended for casual survival worlds**.

Use at your own risk.

---

## 🛠 Development Notes

- Formatting is enforced via **EditorConfig + Spotless**
- AI behavior changes are concentrated under:
  - `funwayguy.esm.ai`
  - `funwayguy.esm.handlers.entities`
- Contributions should preserve **AI determinism and performance constraints**

---

## 🧠 Philosophy

Epic Siege Mod is about **making mobs feel intelligent, hostile, and adaptive**, not simply stronger.

If a mob kills you, it should feel like:
> *You were outplayed — not stat-checked.*

---
