# User Guide

For people using Study LLM to study — not developers. If you're setting up
the project itself, see the root [README](../README.md).

## What Study LLM does

You organize your course material into **Notebooks**. Inside a notebook, you
upload your own documents (PDFs, slides, notes) and ask questions in plain
language. The assistant answers **only** from what you've uploaded — if it's
not in your sources, it tells you instead of guessing — and every answer
shows which document (and page/section, where possible) it came from.

## Getting started

1. **Sign up.** Open the app, go to the sign-up page, and create an account
   with a username, email, and password (entered twice, to confirm).
2. **Log in.** Use your username or email, plus your password. You'll stay
   signed in across visits until you log out.
3. You'll land on **Home**, listing your notebooks (empty at first).

## Creating and managing notebooks

- **Create**: click **+ New Notebook** on Home, give it a name (one per
  course or topic works well), and you're taken straight into it.
- **Rename**: open a notebook's **⋮** menu on its card → **Rename**, edit the
  title, and press Enter (or click away) to save.
- **Delete**: **⋮** menu → **Delete**, then confirm. This is permanent — it
  removes the notebook, everything you uploaded to it, and its whole chat
  history. There's no undo.
- Notebooks are listed most-recently-active first, so the one you're working
  in stays near the top.

## Uploading sources

Inside a notebook, the left panel is **Sources**.

1. Click **+ Add source** and choose a file.
2. **Supported formats**: PDF, DOCX, PPTX, TXT, MD — up to 50MB each.
   Anything else is rejected with an explanation.
3. Each source shows a status:
   - **Processing…** — being read and indexed; usually done within a couple
     of minutes, longer for large files.
   - **Ready** — fully indexed; the assistant can now answer from it.
   - **Failed** — something went wrong reading the file; the reason is shown
     under the source (e.g. an unreadable/corrupt file). Re-upload a fixed
     copy.
4. You can **rename** or **delete** a source from its own **⋮** menu at any
   time; deleting a source removes it from anything the assistant can draw
   on going forward.

You need at least one **Ready** source before you can ask a question — until
then, the chat box prompts you to upload one.

## Asking questions

Type your question into the chat box at the bottom of a notebook and press
**Ask** (or Enter).

- The assistant answers using only your **Ready** sources for that notebook
  — never its own outside knowledge.
- Answers that come from your sources show which document (and page/slide/
  section, when the format supports it) backed the answer.
- If nothing in your sources addresses the question, the assistant says so
  explicitly instead of making something up.
- A source that's still **Processing** isn't used yet — if you just uploaded
  something and ask about it right away, wait for it to finish first.

Your full conversation history for a notebook is kept until you delete the
notebook.

## Finding a notebook

Use the search bar on Home. It matches:

- Notebook **titles**, and
- The **content** of documents you've uploaded — so you can find a notebook
  even if the term you remember only appears inside a source, not in the
  title.

No matches shows a clear "nothing found" message rather than a blank screen.

## Account settings

From **Settings**, you can:

- Update your **username** and **email**.
- Change your **password** (you'll need your current password; the new one
  must be at least 8 characters, entered twice to confirm).

## FAQ

**Can anyone else see my notebooks?**
No. Everything you create is private to your account.

**Why can't the assistant just answer from what it already knows?**
By design — the whole point is answers you can trace back to your own
material, not the model's general knowledge, so you can trust and verify
them.

**Is there a limit on how many notebooks or sources I can have?**
No hard limit.

**Can I recover a deleted notebook or source?**
No — deletion is permanent and immediate.

## Troubleshooting

| Problem | What's happening | What to do |
|---|---|---|
| "Upload a source before asking a question." | The notebook has no `Ready` sources yet. | Upload a document and wait for it to finish processing. |
| A source shows **Failed** | The file couldn't be read (often a corrupted or non-standard export). | Check the shown reason, then try re-exporting/re-saving the file and re-uploading. |
| Upload rejected outright | The file isn't one of the supported formats, or is over 50MB. | Convert/compress it, or upload a supported format (PDF, DOCX, PPTX, TXT, MD). |
| "I couldn't find anything about that in this notebook's sources." | Your sources genuinely don't cover the question. | Upload a source that covers the topic, or rephrase — this message means no relevant match was found, not an error. |
| A source stays **Processing** far longer than expected | Large file, or the server's model backend is slow/unreachable. | Give it a few minutes; if it never resolves, contact whoever runs your instance of the app. |
| Signed out unexpectedly | Your session token expired. | Log in again — your notebooks and history are unaffected. |
