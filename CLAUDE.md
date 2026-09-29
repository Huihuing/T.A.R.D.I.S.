# Claude Code Instructions

@AGENTS.md

## Claude Code specific rules

- `.claude/agents/`의 Subagent는 별도 context가 실제로 필요한 경우에만 사용한다.
- `.claude/skills/`의 Skill은 등록되어 있다는 이유만으로 실행하지 않는다. 모두 사용자가 `/이름`으로 직접 실행한다.
- 단순 작업에는 Subagent를 만들거나 호출하지 않는다.
- MCP는 현재 작업에 실제로 필요한 경우에만 사용한다(AGENTS.md의 MCP Usage Rules).
- 작업이 끝나 상태가 바뀌면 AGENTS.md의 Current State를 갱신한다. 작업 일지처럼 누적하지 않는다.
