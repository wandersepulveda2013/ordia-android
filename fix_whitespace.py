import sys

def fix_file(path):
    with open(path, 'r') as f:
        content = f.read()
    content = content.replace("OrdiaCard(\n                \n                \n            ) {", "OrdiaCard {")
    with open(path, 'w') as f:
        f.write(content)

fix_file('app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt')
