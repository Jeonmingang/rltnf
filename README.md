# PokebuilderLite (Pixelmon 1.16.5)

간단 명령어로 특정 포켓몬의 1~4번 기술 슬롯을 원하는 기술로 바로 교체하는 Spigot 플러그인입니다.
- 서버: 모히스트/마그마/카트서버 등 Spigot+Forge 하이브리드 환경
- 픽셀몬: 1.16.5-9.1.13

## 명령어
- `/pb setmove <partySlot 1-6> <moveSlot 1-4> <move name...>` (자기 자신)
- `/pb setmove <player> <partySlot 1-6> <moveSlot 1-4> <move name...>` (관리자)

예) `/pb setmove 1 2 Flamethrower` → 파티 1번 포켓몬의 2번 기술을 Flamethrower로 교체

## 권한
- `pokebuilder.use` : 기본 true (자기 자신만)
- `pokebuilder.admin` : OP 전용 (다른 플레이어 대상)

## 설치
1) 프로젝트 루트에 `libs/Pixelmon-1.16.5-9.1.13-universal.jar` 파일을 넣으세요. (의존성으로 참조)
2) `mvn package` 로 빌드 → `target/pokebuilder-lite-1.0.0.jar`
3) 서버의 `plugins/` 폴더에 .jar 넣고 재시작

## 주의
- 입력한 기술명이 해당 포켓몬이 배울 수 있는 기술(레벨업/TM/튜터/TR/전이)에 있어야 합니다.
- 교체 시 PP는 자동으로 풀로 회복됩니다.
- 슬롯이 비어있더라도 지정한 인덱스에 바로 세팅합니다.

