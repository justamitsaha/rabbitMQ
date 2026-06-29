# Developer Preferences & Guidelines for AI Coding Agents

This document outlines coding, commenting, and documentation style preferences to guide AI coding agents when working on this repository or creating new modules.

---

## ✍️ Code Commenting Preferences

*   **Concise Method Documentation**: Write comments on method signatures to capture the core essence of the operation. Keep them short (1-2 sentences) and avoid verbose paragraphs.
*   **Layer Exclusion**: Do not add comments or Javadocs to controller layers, REST endpoints, DTO contracts, or data models unless explicitly requested. Keep these clean and self-explanatory.
*   **Critical Alerts**: Use concise inline comments in the code to highlight tricky configurations, non-obvious defaults (e.g., large polling intervals in dev), or potential thread synchronization issues.

---

## 📖 Documentation Structure & Style

*   **Operational Compactness**: Operational guides (e.g., execution flows, validation steps) must be compact, concise, and action-oriented. 
*   **No Long Setup Boilerplate**: Do not copy-paste long lists of manual setup or command-line bootstrap inputs into guides. Instead, point to helper scripts (e.g., shell scripts, compose configurations) and specify the single commands to run or list them.
*   **Separation of Concerns**: Keep operational guides strictly separated from conceptual/theoretical guides:
    *   *Operational Guides*: Focused purely on prerequisites, running monitors, executing verification scenarios, and setup steps.
    *   *Conceptual Guides*: Dedicated to comparative analysis, protocol/service differences, wire specifications, and system architecture.
*   **Rich Cross-Linking**: Always use Markdown links to cross-reference related files, code sources, and configuration properties.

---

## 🕵️‍♂️ Validation & Rollback Scenarios

*   **Boundary Clarification**: When writing validation scenarios, clearly document where checks are enforced (e.g., application service boundaries vs. database constraints).
*   **Rollback Verification**: For every error path or failure scenario:
    1.  Explain the source causing the failure.
    2.  Provide step-by-step verification commands (e.g., SQL select queries) to prove that no dirty state was written and that transactions rolled back successfully.

---

## 🛠 Project Workspace Organization

*   **Doc Directory & README**: 
    *   For related multi-module projects, document the common flow and the operational runbook/guide for the overall project in the main root `README.md`.
    *   Store specific module-related details inside a `.md` file in that module's directory (e.g., detailed Outbox pattern documentation inside `paymentServiceAMQP/`), with only a single line referencing it in the root `README.md`.
    *   Reserve the `doc/` subdirectory for general tech discussions, general technical comparisons, or other patterns that are important but not necessarily implemented.
*   **Centralized Infrastructure Control**: Group infrastructure setup configurations (e.g., Compose files, topic/resource initialization scripts) in a single root-level `doc/` or `infrastructure/` directory rather than duplicating them inside individual service directories.
