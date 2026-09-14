/**
 * AI 情侣助手回复的极简 Markdown 渲染器。
 *
 * 为什么不用 marked / markdown-it：项目当前没有任何 Markdown 依赖，装包需要联网；
 * 而 AI 回复里实际出现的语法是很小的子集（标题、加粗、列表、代码、引用、分割线、链接），
 * 自己实现既能离线可用，也不用为一个气泡多引一个包。
 *
 * 安全策略：先把原文整体做 HTML 转义，之后插入的标签全部由本文件生成，
 * 因此模型输出里的任何标签都只会以文本展示，不可能被执行。
 */

const ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

/** HTML 转义，防止模型输出被当作标签执行 */
function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (char) => ESCAPE_MAP[char])
}

/**
 * 行内语法渲染：行内代码 → 加粗 → 斜体 → 删除线 → 链接
 * 行内代码先抽成占位符，避免代码里的 * _ 被后续规则当成语法解析。
 */
function renderInline(raw: string): string {
  const codes: string[] = []
  let out = escapeHtml(raw).replace(/`([^`]+)`/g, (_match, code: string) => {
    codes.push(code)
    return `\u0000${codes.length - 1}\u0000`
  })

  out = out
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_]+)__/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
    .replace(/~~([^~]+)~~/g, '<del>$1</del>')
    .replace(
      /\[([^\]]+)\]\((https?:\/\/[^\s)]+|\/[^\s)]*)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    )

  return out.replace(/\u0000(\d+)\u0000/g, (_match, index: string) => `<code>${codes[Number(index)]}</code>`)
}

/** 行内起始标记，用于判断段落在哪结束 */
function isBlockStart(line: string): boolean {
  return (
    /^\s*```/.test(line) ||
    /^\s*#{1,6}\s/.test(line) ||
    /^\s*>\s?/.test(line) ||
    /^\s*[-*+]\s/.test(line) ||
    /^\s*\d+[.)]\s/.test(line)
  )
}

/**
 * 把 Markdown 文本渲染成 HTML 字符串。
 *
 * 支持：围栏代码块、# 标题、> 引用、有序/无序列表、--- 分割线、段落，
 * 以及 renderInline 里的行内语法。表格、嵌套列表等复杂语法不在范围内
 * （手机气泡宽度下本来也放不下），会按普通段落展示。
 */
export function renderMarkdown(source: string): string {
  if (!source) return ''

  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const html: string[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    // 围栏代码块
    if (/^\s*```/.test(line)) {
      const code: string[] = []
      i++
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        code.push(lines[i])
        i++
      }
      i++
      html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
      continue
    }

    if (!line.trim()) {
      i++
      continue
    }

    // 分割线
    if (/^\s*([-*_])\s*\1\s*\1[\s\-*_]*$/.test(line)) {
      html.push('<hr>')
      i++
      continue
    }

    // 标题
    const heading = line.match(/^\s*(#{1,6})\s+(.*)$/)
    if (heading) {
      const level = heading[1].length
      html.push(`<h${level}>${renderInline(heading[2].trim())}</h${level}>`)
      i++
      continue
    }

    // 引用
    if (/^\s*>\s?/.test(line)) {
      const quote: string[] = []
      while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
        quote.push(lines[i].replace(/^\s*>\s?/, ''))
        i++
      }
      html.push(`<blockquote>${quote.map(renderInline).join('<br>')}</blockquote>`)
      continue
    }

    // 列表：连续同类标记合并成一个 ul / ol，缩进按同级处理
    const isOrdered = /^\s*\d+[.)]\s/.test(line)
    if (isOrdered || /^\s*[-*+]\s/.test(line)) {
      const items: string[] = []
      while (i < lines.length) {
        const item = isOrdered
          ? lines[i].match(/^\s*\d+[.)]\s+(.*)$/)
          : lines[i].match(/^\s*[-*+]\s+(.*)$/)
        if (!item) break
        items.push(`<li>${renderInline(item[1])}</li>`)
        i++
      }
      const tag = isOrdered ? 'ol' : 'ul'
      html.push(`<${tag}>${items.join('')}</${tag}>`)
      continue
    }

    // 段落：连续普通行合并，行内换行保留为 <br>
    const paragraph: string[] = []
    while (i < lines.length && lines[i].trim() && !isBlockStart(lines[i])) {
      paragraph.push(lines[i])
      i++
    }
    html.push(`<p>${paragraph.map(renderInline).join('<br>')}</p>`)
  }

  return html.join('')
}
