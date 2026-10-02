#!/usr/bin/env python3
from __future__ import annotations

import collections
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def fail(message: str) -> None:
    print("ERROR:", message, file=sys.stderr)
    raise SystemExit(1)


def source_files(*suffixes: str):
    for base in ("runtime", "agents", "connectors", "android/app/src/main"):
        root = ROOT / base
        if not root.exists():
            continue
        for suffix in suffixes:
            yield from root.rglob(f"*{suffix}")


def validate_json_and_xml() -> tuple[int, int]:
    json_count = 0
    for path in ROOT.rglob("*.json"):
        if any(part in {"build", ".gradle"} for part in path.parts):
            continue
        json.loads(path.read_text(encoding="utf-8"))
        json_count += 1

    xml_count = 0
    for path in ROOT.rglob("*.xml"):
        if any(part in {"build", ".gradle"} for part in path.parts):
            continue
        ET.parse(path)
        xml_count += 1

    return json_count, xml_count


def validate_shell_scripts() -> int:
    scripts = sorted((ROOT / "scripts").glob("*.sh"))
    for path in scripts:
        subprocess.run(
            ["bash", "-n", str(path)],
            cwd=ROOT,
            check=True,
        )
    return len(scripts)


def parse_permissions() -> set[str]:
    broker = (
        ROOT
        / "runtime/core/src/main/java/com/kankwj/angcode/runtime/ToolBroker.kt"
    ).read_text(encoding="utf-8")
    match = re.search(r"enum class ToolPermission\s*\{([^}]*)\}", broker, re.S)
    if not match:
        fail("No se encontró enum ToolPermission")

    permissions: set[str] = set()
    for token in re.split(r"[,\n]", match.group(1)):
        candidate = token.strip().split()[0] if token.strip() else ""
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", candidate):
            permissions.add(candidate)
    if not permissions:
        fail("ToolPermission quedó vacío")
    return permissions


def production_code_files():
    for path in source_files(".kt", ".java"):
        parts = path.parts
        if "test" in parts or "androidTest" in parts:
            continue
        yield path


def collect_tool_ids() -> tuple[dict[str, list[str]], set[str], list[str]]:
    tool_locations: dict[str, list[str]] = collections.defaultdict(list)
    id_pattern = re.compile(
        r'override\s+val\s+id(?:\s*:\s*String)?\s*=\s*"([^"]+)"'
    )

    for path in production_code_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        for tool_id in id_pattern.findall(text):
            tool_locations[tool_id].append(str(path.relative_to(ROOT)))

    catalog_path = (
        ROOT
        / "runtime/core/src/main/java/com/kankwj/angcode/runtime/ToolCatalog.kt"
    )
    catalog = catalog_path.read_text(encoding="utf-8")
    catalog_ids = re.findall(r'ToolCapability\("([^"]+)"', catalog)
    wildcard_patterns = [item for item in catalog_ids if item.endswith("*")]
    exact_catalog = {item for item in catalog_ids if not item.endswith("*")}

    duplicates = {
        tool_id: locations
        for tool_id, locations in tool_locations.items()
        if len(locations) > 1
    }
    if duplicates:
        fail("IDs de herramientas duplicados: " + json.dumps(duplicates, ensure_ascii=False))

    return tool_locations, exact_catalog, wildcard_patterns


def validate_toolpacks() -> tuple[int, int]:
    permissions = parse_permissions()
    tool_locations, exact_catalog, wildcard_patterns = collect_tool_ids()
    static_ids = set(tool_locations)

    pack_ids: set[str] = set()
    tool_count = 0

    def covered(tool_id: str) -> bool:
        if tool_id in static_ids or tool_id in exact_catalog:
            return True
        return any(tool_id.startswith(pattern[:-1]) for pattern in wildcard_patterns)

    for path in sorted((ROOT / "toolpacks").rglob("toolpack.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        pack_id = data["id"]
        if pack_id in pack_ids:
            fail(f"Tool Pack duplicado: {pack_id}")
        pack_ids.add(pack_id)

        tools = data.get("tools", [])
        if len(tools) != len(set(tools)):
            fail(f"Tool Pack {pack_id} contiene herramientas duplicadas")

        declared_permissions = data.get("permissions", [])
        if len(declared_permissions) != len(set(declared_permissions)):
            fail(f"Tool Pack {pack_id} contiene permisos duplicados")

        unknown_permissions = sorted(set(declared_permissions) - permissions)
        if unknown_permissions:
            fail(
                f"Tool Pack {pack_id} usa permisos desconocidos: "
                + ", ".join(unknown_permissions)
            )

        missing_tools = [tool_id for tool_id in tools if not covered(tool_id)]
        if missing_tools:
            fail(
                f"Tool Pack {pack_id} referencia herramientas no registradas: "
                + ", ".join(missing_tools)
            )

        produces_artifacts = any(
            tool_id.startswith("artifact.")
            or tool_id == "browser.screenshot"
            or tool_id in {"media.convert", "image.convert"}
            for tool_id in tools
        )
        if produces_artifacts and "ARTIFACT_WRITE" not in declared_permissions:
            fail(
                f"Tool Pack {pack_id} produce artifacts pero no declara ARTIFACT_WRITE"
            )

        runtime = data.get("runtime", "ANDROID_NATIVE")
        if runtime not in {"ANDROID_NATIVE", "PROOT"}:
            fail(f"Tool Pack {pack_id} usa runtime desconocido: {runtime}")

        architectures = data.get("architectures", [])
        if not isinstance(architectures, list):
            fail(f"Tool Pack {pack_id} architectures debe ser una lista")

        tool_count += len(tools)

    return len(pack_ids), tool_count


def validate_kotlin_imports() -> int:
    checked = 0
    for path in source_files(".kt"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        imports = [
            line.strip()
            for line in text.splitlines()
            if line.startswith("import ")
        ]
        duplicates = [
            item
            for item, count in collections.Counter(imports).items()
            if count > 1
        ]
        if duplicates:
            fail(
                f"Imports Kotlin duplicados en {path.relative_to(ROOT)}: "
                + ", ".join(duplicates)
            )
        checked += 1
    return checked


def validate_manifest() -> None:
    manifest_path = ROOT / "android/app/src/main/AndroidManifest.xml"
    root = ET.parse(manifest_path).getroot()
    application = root.find("application")
    if application is None:
        fail("AndroidManifest sin <application>")

    if application.get(ANDROID_NS + "allowBackup") != "false":
        fail("android:allowBackup debe permanecer false")

    if application.get(ANDROID_NS + "debuggable") == "true":
        fail("android:debuggable no puede fijarse a true en el manifest")

    if application.get(ANDROID_NS + "usesCleartextTraffic") == "true":
        fail("android:usesCleartextTraffic no puede fijarse a true globalmente")

    unsafe_exported: list[str] = []
    for tag in ("service", "receiver", "provider"):
        for component in application.findall(tag):
            if component.get(ANDROID_NS + "exported") == "true":
                permission = component.get(ANDROID_NS + "permission")
                if not permission:
                    unsafe_exported.append(
                        f"{tag}:{component.get(ANDROID_NS + 'name')}"
                    )
    if unsafe_exported:
        fail(
            "Componentes sensibles exportados sin permiso: "
            + ", ".join(unsafe_exported)
        )


def validate_no_build_artifacts() -> None:
    tracked_like = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if "build" in path.parts or ".gradle" in path.parts:
            tracked_like.append(str(path.relative_to(ROOT)))
    if tracked_like:
        fail(
            "El snapshot contiene artefactos de build inesperados: "
            + ", ".join(tracked_like[:20])
        )


def main() -> None:
    json_count, xml_count = validate_json_and_xml()
    shell_count = validate_shell_scripts()
    kotlin_count = validate_kotlin_imports()
    pack_count, pack_tool_count = validate_toolpacks()
    validate_manifest()
    validate_no_build_artifacts()

    print(
        "VALIDATION_OK",
        f"json={json_count}",
        f"xml={xml_count}",
        f"shell={shell_count}",
        f"kotlin={kotlin_count}",
        f"toolpacks={pack_count}",
        f"pack_tools={pack_tool_count}",
    )


if __name__ == "__main__":
    main()
