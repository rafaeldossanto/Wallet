import 'dart:typed_data';

import 'package:flutter_svg/flutter_svg.dart';

/// Loads an institution's SVG logo from the URL Pluggy sends.
///
/// Pluggy's logos colour their shapes through a `<style>` block of class rules, and flutter_svg
/// reads only `style` attributes: the Nubank "nu" would come out black over a black disc. This
/// loader moves those rules onto the elements before the SVG is drawn.
class InstitutionLogoLoader extends SvgNetworkLoader {
  const InstitutionLogoLoader(super.url);

  @override
  String provideSvg(Uint8List? message) => inlineSvgStyles(super.provideSvg(message));

  @override
  bool operator ==(Object other) => other is InstitutionLogoLoader && other.url == url;

  @override
  int get hashCode => Object.hash(InstitutionLogoLoader, url);
}

final _styleBlock = RegExp(r'<style[^>]*>([\s\S]*?)</style>');
final _cssRule = RegExp(r'([^{}]+)\{([^}]*)\}');
final _tag = RegExp(r'<([A-Za-z][\w:.-]*)(\s[^>]*?)?(/?)>');
final _classAttribute = RegExp(r'''\sclass\s*=\s*["']([^"']*)["']''');
final _styleAttribute = RegExp(r'''\sstyle\s*=\s*["']([^"']*)["']''');

/// Moves the class rules of an SVG's `<style>` blocks into the `style` attribute of each element
/// that uses them, and drops the blocks. Only plain class selectors (`.a`, `.a, .b`) are applied,
/// which is what logo exporters write. An element's own `style` wins over its classes, as in CSS.
String inlineSvgStyles(String svg) {
  final rules = <String, List<String>>{};
  for (final block in _styleBlock.allMatches(svg)) {
    final css = block.group(1)!
        .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '')
        .replaceAll('<![CDATA[', '')
        .replaceAll(']]>', '');
    for (final rule in _cssRule.allMatches(css)) {
      final declarations = _declarations(rule.group(2)!);
      for (final selector in rule.group(1)!.split(',').map((selector) => selector.trim())) {
        if (RegExp(r'^\.[\w-]+$').hasMatch(selector)) {
          rules.putIfAbsent(selector.substring(1), () => []).addAll(declarations);
        }
      }
    }
  }
  final withoutBlocks = svg.replaceAll(_styleBlock, '');
  if (rules.isEmpty) {
    return withoutBlocks;
  }
  return withoutBlocks.replaceAllMapped(_tag, (tag) {
    final attributes = tag.group(2) ?? '';
    final classes = _classAttribute.firstMatch(attributes);
    if (classes == null) {
      return tag.group(0)!;
    }
    final fromClasses = [
      for (final name in classes.group(1)!.split(RegExp(r'\s+'))) ...?rules[name],
    ];
    if (fromClasses.isEmpty) {
      return tag.group(0)!;
    }
    var rest = attributes.replaceRange(classes.start, classes.end, '');
    final own = _styleAttribute.firstMatch(rest);
    final style = [...fromClasses, if (own != null) ..._declarations(own.group(1)!)].join(';');
    rest = own == null ? '$rest style="$style"' : rest.replaceRange(own.start, own.end, ' style="$style"');
    return '<${tag.group(1)}$rest${tag.group(3)}>';
  });
}

/// `fill: #820ad1; ;stroke:none` → `[fill:#820ad1, stroke:none]`: flutter_svg trips over an empty
/// or blank declaration.
List<String> _declarations(String css) => [
      for (final declaration in css.split(';'))
        if (declaration.contains(':'))
          '${declaration.substring(0, declaration.indexOf(':')).trim()}:${declaration.substring(declaration.indexOf(':') + 1).trim()}',
    ];
