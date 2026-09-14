# Local images folder

While the app is localhosted, images are loaded from this folder instead of a web
server.

## How it works
`ImageResolver.resolve(...)` turns a stored image reference into a loadable URL.
If the value is a **bare filename or relative path**, it is looked up here
(`client/images/`). Full `http(s)://`, `file:`, and `data:` URLs still
work unchanged, and absolute filesystem paths are also accepted.

Used by both the profile **avatar** and the **question image** (visual aid).
When a teacher uploads a `.png` in the Question Bank, the file is copied here via
`ImageResolver.imagesDir()` and its filename is saved as the question's
`image_url`; the student's Take Exam screen then renders it.

## Usage
1. Drop an image file in this folder, e.g. `me.png`.
2. In **Settings → Profile → Avatar image**, type just the filename: `me.png`.
3. Save. The avatar renders from `client/images/me.png`.

Subfolders are fine too — use a relative path like `avatars/me.png`.

## Notes
- No rebuild is needed; files are read from disk at runtime.
- Resolution tries the module dir and the workspace root, so it works whether the
  client is launched from `client/` or the repository root.
- Question `image_url` values resolve the same way if/when question images are
  displayed — reuse `resolveImageUrl(...)`.
