export interface MathSymbolItem {
  id: string;
  label: string;
  display: string;
  latex: string;
  cursorOffset?: number;
}

export interface MathSymbolCategory {
  id: string;
  title: string;
  shortTitle: string;
  symbols: MathSymbolItem[];
}

export const MATH_SYMBOL_CATEGORIES: MathSymbolCategory[] = [
  {
    id: 'basic_arithmetic_algebra',
    title: 'Basic Arithmetic & Algebra',
    shortTitle: 'Arithmetic & Algebra',
    symbols: [
      { id: 'plus', label: 'Plus', display: '+', latex: '+' },
      { id: 'minus', label: 'Minus', display: '−', latex: '-' },
      { id: 'plus_minus', label: 'Plus-Minus', display: '±', latex: '\\pm ' },
      { id: 'minus_plus', label: 'Minus-Plus', display: '∓', latex: '\\mp ' },
      { id: 'mult_cross', label: 'Multiplication Cross', display: '×', latex: '\\times ' },
      { id: 'mult_dot', label: 'Multiplication Dot', display: '·', latex: '\\cdot ' },
      { id: 'division', label: 'Division', display: '÷', latex: '\\div ' },
      { id: 'equal_to', label: 'Equal To', display: '=', latex: '=' },
      { id: 'not_equal_to', label: 'Not Equal To', display: '≠', latex: '\\neq ' },
      { id: 'approx_equal', label: 'Approximately Equal', display: '≈', latex: '\\approx ' },
      { id: 'less_equal', label: 'Less Than or Equal To', display: '≤', latex: '\\leq ' },
      { id: 'greater_equal', label: 'Greater Than or Equal To', display: '≥', latex: '\\geq ' },
    ],
  },
  {
    id: 'formats_layout_templates',
    title: 'Formats & Layout Templates',
    shortTitle: 'Formats & Layout',
    symbols: [
      { id: 'fraction', label: 'Fraction', display: 'a/b', latex: '\\frac{a}{b}' },
      { id: 'superscript', label: 'Superscript (Power)', display: 'xⁿ', latex: 'x^{n}' },
      { id: 'subscript', label: 'Subscript (Base)', display: 'xₙ', latex: 'x_{n}' },
      { id: 'sqrt', label: 'Square Root', display: '√x', latex: '\\sqrt{x}' },
      { id: 'nth_root', label: 'N-th Root', display: 'ⁿ√x', latex: '\\sqrt[n]{x}' },
      { id: 'round_brackets', label: 'Parentheses (Round Brackets)', display: '(x)', latex: '\\left( x \\right)' },
      { id: 'square_brackets', label: 'Square Brackets', display: '[x]', latex: '\\left[ x \\right]' },
      { id: 'curly_brackets', label: 'Curly Brackets (Braces)', display: '{x}', latex: '\\left\\{ x \\right\\}' },
      { id: 'abs_value', label: 'Absolute Value (Modulus)', display: '|x|', latex: '\\left| x \\right|' },
      { id: 'norm_double_bar', label: 'Norm (Double Bar)', display: '‖x‖', latex: '\\left\\| x \\right\\|' },
    ],
  },
  {
    id: 'greek_letters_constants',
    title: 'Greek Letters & Constants',
    shortTitle: 'Greek & Constants',
    symbols: [
      { id: 'alpha', label: 'Alpha', display: 'α', latex: '\\alpha ' },
      { id: 'beta', label: 'Beta', display: 'β', latex: '\\beta ' },
      { id: 'gamma', label: 'Gamma', display: 'γ', latex: '\\gamma ' },
      { id: 'delta_cap', label: 'Delta (Capital)', display: 'Δ', latex: '\\Delta ' },
      { id: 'delta_low', label: 'Delta (Lowercase)', display: 'δ', latex: '\\delta ' },
      { id: 'epsilon', label: 'Epsilon', display: 'ε', latex: '\\epsilon ' },
      { id: 'theta', label: 'Theta', display: 'θ', latex: '\\theta ' },
      { id: 'lambda', label: 'Lambda', display: 'λ', latex: '\\lambda ' },
      { id: 'mu', label: 'Mu', display: 'μ', latex: '\\mu ' },
      { id: 'pi', label: 'Pi', display: 'π', latex: '\\pi ' },
      { id: 'rho', label: 'Rho', display: 'ρ', latex: '\\rho ' },
      { id: 'sigma_cap', label: 'Sigma (Capital)', display: 'Σ', latex: '\\Sigma ' },
      { id: 'sigma_low', label: 'Sigma (Lowercase)', display: 'σ', latex: '\\sigma ' },
      { id: 'phi', label: 'Phi', display: 'φ', latex: '\\phi ' },
      { id: 'psi', label: 'Psi', display: 'ψ', latex: '\\psi ' },
      { id: 'omega_cap', label: 'Omega (Capital)', display: 'Ω', latex: '\\Omega ' },
      { id: 'omega_low', label: 'Omega (Lowercase)', display: 'ω', latex: '\\omega ' },
      { id: 'euler_e', label: "Euler's Constant", display: 'e', latex: 'e' },
      { id: 'imaginary_i', label: 'Imaginary Unit', display: 'i', latex: 'i' },
      { id: 'infinity', label: 'Infinity', display: '∞', latex: '\\infty ' },
    ],
  },
  {
    id: 'geometry_trigonometry',
    title: 'Geometry & Trigonometry',
    shortTitle: 'Geometry & Trig',
    symbols: [
      { id: 'sine', label: 'Sine', display: 'sin', latex: '\\sin(\\theta)' },
      { id: 'cosine', label: 'Cosine', display: 'cos', latex: '\\cos(\\theta)' },
      { id: 'tangent', label: 'Tangent', display: 'tan', latex: '\\tan(\\theta)' },
      { id: 'cotangent', label: 'Cotangent', display: 'cot', latex: '\\cot(\\theta)' },
      { id: 'secant', label: 'Secant', display: 'sec', latex: '\\sec(\\theta)' },
      { id: 'cosecant', label: 'Cosecant', display: 'csc', latex: '\\csc(\\theta)' },
      { id: 'angle', label: 'Angle', display: '∠', latex: '\\angle ' },
      { id: 'degree', label: 'Degree', display: '°', latex: '^{\\circ}' },
      { id: 'perpendicular', label: 'Perpendicular', display: '⊥', latex: '\\perp ' },
      { id: 'parallel', label: 'Parallel', display: '∥', latex: '\\parallel ' },
    ],
  },
  {
    id: 'calculus_advanced_math',
    title: 'Calculus & Advanced Math',
    shortTitle: 'Calculus & Advanced',
    symbols: [
      { id: 'indef_integral', label: 'Indefinite Integral', display: '∫', latex: '\\int f(x)\\,dx' },
      { id: 'def_integral', label: 'Definite Integral', display: '∫ₐᵇ', latex: '\\int_{a}^{b} f(x)\\,dx' },
      { id: 'double_integral', label: 'Double Integral', display: '∬', latex: '\\iint_{D} f(x,y)\\,dA' },
      { id: 'summation', label: 'Summation (Sigma)', display: '∑', latex: '\\sum_{i=1}^{n} a_{i}' },
      { id: 'product_pi', label: 'Product (Pi)', display: '∏', latex: '\\prod_{i=1}^{n} a_{i}' },
      { id: 'limit', label: 'Limit', display: 'lim', latex: '\\lim_{x \\to \\infty} f(x)' },
      { id: 'derivative_dx', label: 'Derivative (dx)', display: 'd/dx', latex: '\\frac{dy}{dx}' },
      { id: 'partial_derivative', label: 'Partial Derivative', display: '∂', latex: '\\frac{\\partial f}{\\partial x}' },
      { id: 'nabla', label: 'Nabla (Gradient)', display: '∇', latex: '\\nabla f' },
    ],
  },
  {
    id: 'sets_logic',
    title: 'Sets & Logic',
    shortTitle: 'Sets & Logic',
    symbols: [
      { id: 'element_of', label: 'Element Of', display: '∈', latex: '\\in ' },
      { id: 'not_element_of', label: 'Not Element Of', display: '∉', latex: '\\notin ' },
      { id: 'subset_of', label: 'Subset Of', display: '⊂', latex: '\\subset ' },
      { id: 'union', label: 'Union', display: '∪', latex: '\\cup ' },
      { id: 'intersection', label: 'Intersection', display: '∩', latex: '\\cap ' },
      { id: 'empty_set', label: 'Empty Set', display: '∅', latex: '\\emptyset ' },
      { id: 'logical_and', label: 'Logical AND (Conjunction)', display: '∧', latex: '\\land ' },
      { id: 'logical_or', label: 'Logical OR (Disjunction)', display: '∨', latex: '\\lor ' },
      { id: 'therefore', label: 'Therefore', display: '∴', latex: '\\therefore ' },
    ],
  },
];

const LATEX_TOKEN_MAP: Record<string, string> = {
  // Arithmetic & Algebra
  pm: '±',
  mp: '∓',
  times: '×',
  cdot: '·',
  div: '÷',
  neq: '≠',
  ne: '≠',
  approx: '≈',
  leq: '≤',
  le: '≤',
  geq: '≥',
  ge: '≥',
  equiv: '≡',
  sim: '∼',
  propto: '∝',

  // Greek Letters & Constants
  alpha: 'α',
  beta: 'β',
  gamma: 'γ',
  Gamma: 'Γ',
  Delta: 'Δ',
  delta: 'δ',
  epsilon: 'ε',
  varepsilon: 'ε',
  zeta: 'ζ',
  eta: 'η',
  theta: 'θ',
  Theta: 'Θ',
  iota: 'ι',
  kappa: 'κ',
  lambda: 'λ',
  Lambda: 'Λ',
  mu: 'μ',
  nu: 'ν',
  xi: 'ξ',
  Xi: 'Ξ',
  pi: 'π',
  Pi: 'Π',
  rho: 'ρ',
  Sigma: 'Σ',
  sigma: 'σ',
  tau: 'τ',
  upsilon: 'υ',
  Phi: 'Φ',
  phi: 'φ',
  varphi: 'φ',
  chi: 'χ',
  Psi: 'Ψ',
  psi: 'ψ',
  Omega: 'Ω',
  omega: 'ω',
  infty: '∞',

  // Geometry & Trigonometry
  sin: '<span class="wiki-latex-op">sin</span>',
  cos: '<span class="wiki-latex-op">cos</span>',
  tan: '<span class="wiki-latex-op">tan</span>',
  cot: '<span class="wiki-latex-op">cot</span>',
  sec: '<span class="wiki-latex-op">sec</span>',
  csc: '<span class="wiki-latex-op">csc</span>',
  arcsin: '<span class="wiki-latex-op">arcsin</span>',
  arccos: '<span class="wiki-latex-op">arccos</span>',
  arctan: '<span class="wiki-latex-op">arctan</span>',
  ln: '<span class="wiki-latex-op">ln</span>',
  log: '<span class="wiki-latex-op">log</span>',
  angle: '∠',
  circ: '°',
  degree: '°',
  perp: '⊥',
  parallel: '∥',

  // Calculus & Advanced Math
  int: '<span class="wiki-latex-bigop">∫</span>',
  iint: '<span class="wiki-latex-bigop">∬</span>',
  iiint: '<span class="wiki-latex-bigop">∭</span>',
  oint: '<span class="wiki-latex-bigop">∮</span>',
  sum: '<span class="wiki-latex-bigop">∑</span>',
  prod: '<span class="wiki-latex-bigop">∏</span>',
  lim: '<span class="wiki-latex-op">lim</span>',
  partial: '∂',
  nabla: '∇',
  to: '→',
  rightarrow: '→',
  leftarrow: '←',
  Rightarrow: '⇒',
  Leftrightarrow: '⇔',

  // Sets & Logic
  in: '∈',
  notin: '∉',
  subset: '⊂',
  subseteq: '⊆',
  supset: '⊃',
  supseteq: '⊇',
  cup: '∪',
  cap: '∩',
  emptyset: '∅',
  varnothing: '∅',
  land: '∧',
  wedge: '∧',
  lor: '∨',
  vee: '∨',
  therefore: '∴',
  because: '∵',
  forall: '∀',
  exists: '∃',
  neg: '¬',
};

function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function extractBalancedGroup(
  src: string,
  startIdx: number,
  openChar: string = '{',
  closeChar: string = '}'
): { content: string; nextIdx: number } | null {
  if (src[startIdx] !== openChar) return null;
  let depth = 0;
  for (let i = startIdx; i < src.length; i++) {
    const ch = src[i];
    if (ch === '\\') {
      i++; // skip escaped char
      continue;
    }
    if (ch === openChar) depth++;
    else if (ch === closeChar) {
      depth--;
      if (depth === 0) {
        return {
          content: src.slice(startIdx + 1, i),
          nextIdx: i + 1,
        };
      }
    }
  }
  // Unclosed group fallback: consume till end
  return {
    content: src.slice(startIdx + 1),
    nextIdx: src.length,
  };
}

function skipSpaces(src: string, idx: number): number {
  let i = idx;
  while (i < src.length && /\s/.test(src[i])) i++;
  return i;
}

function readSingleTokenOrGroup(
  src: string,
  startIdx: number
): { html: string; nextIdx: number } {
  const i = skipSpaces(src, startIdx);
  if (i >= src.length) return { html: '', nextIdx: i };

  if (src[i] === '{') {
    const grp = extractBalancedGroup(src, i, '{', '}');
    if (grp) {
      return {
        html: renderLatexExpressionToHtml(grp.content),
        nextIdx: grp.nextIdx,
      };
    }
  }

  if (src[i] === '\\') {
    let j = i + 1;
    if (j < src.length && /[a-zA-Z]/.test(src[j])) {
      while (j < src.length && /[a-zA-Z]/.test(src[j])) j++;
      const cmd = src.slice(i + 1, j);
      const mapped = LATEX_TOKEN_MAP[cmd] ?? escapeHtml(cmd);
      return { html: mapped, nextIdx: j };
    }
    if (j < src.length) {
      const sym = src[j];
      if (sym === '{' || sym === '}' || sym === '|') {
        return { html: sym === '|' ? '‖' : escapeHtml(sym), nextIdx: j + 1 };
      }
      return { html: escapeHtml(sym), nextIdx: j + 1 };
    }
  }

  return {
    html: escapeHtml(src[i]),
    nextIdx: i + 1,
  };
}

/**
 * Pure, zero-dependency, inline-safe LaTeX mathematical expression renderer.
 * Produces compact inline HTML (`display: inline-flex; vertical-align: middle`)
 * that NEVER disturbs surrounding words, line heights, or sentences in the paragraph.
 */
export function renderLatexExpressionToHtml(rawLatex: string): string {
  const src = (rawLatex || '').trim().replace(/^\$+|\$+$/g, '');
  if (!src) return '';

  let out = '';
  let i = 0;

  while (i < src.length) {
    const ch = src[i];

    // Whitespace
    if (/\s/.test(ch)) {
      out += ' ';
      while (i < src.length && /\s/.test(src[i])) i++;
      continue;
    }

    // Subscript / Superscript attachment (`_`, `^`)
    if (ch === '^' || ch === '_') {
      let supHtml: string | null = null;
      let subHtml: string | null = null;

      let cur = i;
      while (cur < src.length && (src[cur] === '^' || src[cur] === '_')) {
        const kind = src[cur];
        const parsed = readSingleTokenOrGroup(src, cur + 1);
        if (kind === '^') supHtml = parsed.html;
        else subHtml = parsed.html;
        cur = skipSpaces(src, parsed.nextIdx);
      }

      if (supHtml !== null && subHtml !== null) {
        out += `<span class="wiki-latex-supsub"><sup class="wiki-latex-sup">${supHtml}</sup><sub class="wiki-latex-sub">${subHtml}</sub></span>`;
      } else if (supHtml !== null) {
        out += `<sup class="wiki-latex-sup">${supHtml}</sup>`;
      } else if (subHtml !== null) {
        out += `<sub class="wiki-latex-sub">${subHtml}</sub>`;
      }
      i = cur;
      continue;
    }

    // Group `{ ... }`
    if (ch === '{') {
      const grp = extractBalancedGroup(src, i, '{', '}');
      if (grp) {
        out += renderLatexExpressionToHtml(grp.content);
        i = grp.nextIdx;
        continue;
      }
    }

    // LaTeX Command `\...`
    if (ch === '\\') {
      let j = i + 1;
      if (j >= src.length) {
        i++;
        continue;
      }

      // Escaped delimiter commands like `\,`, `\;`, `\{`, `\}`, `\|`
      if (!/[a-zA-Z]/.test(src[j])) {
        const esc = src[j];
        if (esc === ',' || esc === ';' || esc === ':' || esc === ' ') {
          out += '&thinsp;';
        } else if (esc === '!') {
          // negative thin space ignore
        } else if (esc === '|') {
          out += '<span class="wiki-latex-delim">‖</span>';
        } else if (esc === '{' || esc === '}') {
          out += `<span class="wiki-latex-delim">${esc}</span>`;
        } else {
          out += escapeHtml(esc);
        }
        i = j + 1;
        continue;
      }

      while (j < src.length && /[a-zA-Z]/.test(src[j])) j++;
      const cmd = src.slice(i + 1, j);

      // Ignore sizing prefixes `\left` and `\right`
      if (cmd === 'left' || cmd === 'right') {
        const k = skipSpaces(src, j);
        if (k < src.length) {
          if (src[k] === '\\') {
            if (src[k + 1] === '{' || src[k + 1] === '}') {
              out += `<span class="wiki-latex-delim">${src[k + 1]}</span>`;
              i = k + 2;
              continue;
            }
            if (src[k + 1] === '|') {
              out += '<span class="wiki-latex-delim">‖</span>';
              i = k + 2;
              continue;
            }
          } else if ('()[]|'.includes(src[k])) {
            out += `<span class="wiki-latex-delim">${escapeHtml(src[k])}</span>`;
            i = k + 1;
            continue;
          } else if (src[k] === '.') {
            i = k + 1;
            continue;
          }
        }
        i = j;
        continue;
      }

      // Fraction `\frac{num}{den}` or `\dfrac{num}{den}`
      if (cmd === 'frac' || cmd === 'dfrac' || cmd === 'tfrac') {
        const numParsed = readSingleTokenOrGroup(src, j);
        const denParsed = readSingleTokenOrGroup(src, numParsed.nextIdx);
        out += `<span class="wiki-latex-frac"><span class="wiki-latex-frac-num">${numParsed.html}</span><span class="wiki-latex-frac-den">${denParsed.html}</span></span>`;
        i = denParsed.nextIdx;
        continue;
      }

      // Square Root & N-th Root `\sqrt{x}` or `\sqrt[n]{x}`
      if (cmd === 'sqrt') {
        let k = skipSpaces(src, j);
        let rootIndexHtml = '';
        if (src[k] === '[') {
          const optGrp = extractBalancedGroup(src, k, '[', ']');
          if (optGrp) {
            rootIndexHtml = renderLatexExpressionToHtml(optGrp.content);
            k = optGrp.nextIdx;
          }
        }
        const radParsed = readSingleTokenOrGroup(src, k);
        if (rootIndexHtml) {
          out += `<span class="wiki-latex-sqrt"><sup class="wiki-latex-root-idx">${rootIndexHtml}</sup><span class="wiki-latex-radical">√</span><span class="wiki-latex-radicand">${radParsed.html}</span></span>`;
        } else {
          out += `<span class="wiki-latex-sqrt"><span class="wiki-latex-radical">√</span><span class="wiki-latex-radicand">${radParsed.html}</span></span>`;
        }
        i = radParsed.nextIdx;
        continue;
      }

      // Text inside math `\text{...}` or `\mathrm{...}`
      if (cmd === 'text' || cmd === 'mathrm' || cmd === 'mathbf') {
        const txtParsed = readSingleTokenOrGroup(src, j);
        out += `<span class="wiki-latex-op">${txtParsed.html}</span>`;
        i = txtParsed.nextIdx;
        continue;
      }

      const mapped = LATEX_TOKEN_MAP[cmd];
      if (mapped !== undefined) {
        out += mapped;
      } else {
        out += `<span class="wiki-latex-op">${escapeHtml(cmd)}</span>`;
      }
      i = j;
      continue;
    }

    // Operators & Digits vs Italic Math Variables
    if (/[a-zA-Z]/.test(ch)) {
      out += `<var class="wiki-latex-var">${escapeHtml(ch)}</var>`;
    } else if (ch === '-') {
      out += '−';
    } else if (ch === '*') {
      out += '·';
    } else {
      out += escapeHtml(ch);
    }
    i++;
  }

  return out;
}
