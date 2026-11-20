# Homepage avatar

The approved Q-style mascot and nine additional frames were made with the built-in imagegen tool using the owner's portrait as the identity reference. Original generated PNGs remain in the local image-generation library; the website uses the ten 512 × 512 transparent WebP files in `gaze/` (about 250 KiB total).

`src/components/InteractiveAvatar.vue` is used by `pages/index.md`. Direction names refer to the viewer's screen, not the character's left and right. The eight gaze frames change eye direction while the head and body stay facing forward; this version does not contain head-turn or waving videos.

## Generation prompts

Approved character prompt: turn the supplied portrait-derived avatar into a much more cartoonish 3D mascot, with an oversized rounded head, compact torso, large expressive dark eyes, simple eyebrows, a tiny button nose and a clean curved smile. Keep the short chunky black fringe, cream crewneck shirt and recognizable appearance. Use smooth matte clay surfaces, front-facing relaxed pose, soft studio light and a transparent background. No photographic skin, props, lettering or watermark.

For each gaze frame, the approved cartoon image was used directly as the reference with this prompt:

> Create one exact matching animation frame. Keep identical character identity, face shapes, huge head, chunky black hair, eyebrows, cream shirt, body, pose, scale, camera framing, light and transparent background. Keep everything outside the eyes unchanged as much as possible. Do not rotate or tilt the head or body. Change only the eyeball gaze. Both pupils look toward [left / right / up / down / upper left / upper right / lower left / lower right] of the image, from the viewer's perspective. Keep the canvas dimensions and placement. No text or extra objects.

Blink prompt:

> Change only both eyes to fully shut, with simple gently curved dark eyelid lines and no visible eyeballs. Keep the exact head position, hair, eyebrows, cheeks, nose, smile, ears, neck, shirt, body, framing, scale, colors, lighting and transparent background. Do not tilt or rotate anything.

AI-generated frames can have small differences outside the eyes. They use matching canvas dimensions and are displayed at a small size, with no opacity crossfade between frames.

## Behavior

- Desktop mouse and pen movement choose one of eight gaze directions; the area immediately around the eyes uses the center frame.
- A frame is used only after decoding; failed optional frames fall back to center.
- Blinking happens every 3–6 seconds while visible. Clicking (or Enter/Space on the button) shows a greeting and a small sway.
- Touch users can tap to greet. Reduced-motion preferences disable gaze tracking, blinking and CSS animations, while keeping greetings.
- Timers, observers, animation frames and event listeners are removed when leaving the page.

Preview with `corepack pnpm dev` and open `http://localhost:3333/`. Changes are local until committed and pushed through the repository's existing GitHub Pages workflow.
