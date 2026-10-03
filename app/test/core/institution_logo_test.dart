import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/widgets/institution_logo.dart';

void main() {
  // Shaped like the logos Pluggy serves: a transparent disc and a mark coloured by class.
  const logo = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 500">'
      '<defs><style>.cls-1{fill:none;}.cls-2{fill:#820ad1;}</style></defs>'
      '<g id="fundo"><rect class="cls-1" width="500" height="500" rx="250"/></g>'
      '<g id="logos"><path id="mark" class="cls-2" d="M0,0H10V10Z"/></g></svg>';

  test('class rules move onto the elements and the style block goes', () {
    final svg = inlineSvgStyles(logo);

    expect(svg, isNot(contains('<style')));
    expect(svg, isNot(contains('class=')));
    expect(svg, contains('<rect width="500" height="500" rx="250" style="fill:none"/>'),
        reason: 'the disc stays transparent instead of painting black');
    expect(svg, contains('<path id="mark" d="M0,0H10V10Z" style="fill:#820ad1"/>'));
    expect(svg, contains('<g id="logos">'), reason: 'elements without a class are left alone');
  });

  test('an element\'s own style wins over its classes, and selector lists and several classes apply', () {
    const source = '<svg><style>/* exported */ .a, .b { fill : red ; stroke:blue } .c{opacity:.5}</style>'
        '<path class="b c" style="fill:green" d="M0,0"/><circle class="unknown" r="1"/></svg>';

    final svg = inlineSvgStyles(source);

    expect(svg, contains('<path style="fill:red;stroke:blue;opacity:.5;fill:green" d="M0,0"/>'),
        reason: 'the later fill:green is the one that counts');
    expect(svg, contains('<circle class="unknown" r="1"/>'), reason: 'a class with no rule stays as it was');
  });

  test('an SVG without a style block comes back as it was', () {
    const plain = '<svg viewBox="0 0 10 10"><path fill="#000" d="M0,0"/></svg>';

    expect(inlineSvgStyles(plain), plain);
  });
}
