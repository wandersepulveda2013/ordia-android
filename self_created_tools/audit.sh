#!/bin/bash
echo "== AUDITING UI COMPONENTS =="
grep -rn "OrdiaCard" app/src/main/java/com/ordia/app/
grep -rn "OrdiaButton" app/src/main/java/com/ordia/app/
grep -rn "OrdiaTextField" app/src/main/java/com/ordia/app/
