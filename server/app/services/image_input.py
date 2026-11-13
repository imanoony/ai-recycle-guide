from __future__ import annotations

import warnings
from dataclasses import dataclass
from io import BytesIO

from PIL import Image, ImageOps, UnidentifiedImageError

MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_IMAGE_PIXELS = 24_000_000
MAX_IMAGE_SIDE = 4_096
SUPPORTED_FORMATS = {"JPEG", "PNG", "WEBP"}


class ImageTooLargeError(ValueError):
    """The encoded upload or decoded image dimensions exceed configured limits."""


class InvalidImageError(ValueError):
    """The upload is empty, corrupt, or not a supported image format."""


@dataclass(frozen=True)
class PreparedImage:
    data: bytes
    content_type: str = "image/jpeg"
    filename: str = "upload.jpg"


def prepare_image(data: bytes) -> PreparedImage:
    """Decode a supported image, apply EXIF orientation, and normalize it to JPEG."""
    if not data:
        raise InvalidImageError("The uploaded file is empty.")
    if len(data) > MAX_UPLOAD_BYTES:
        raise ImageTooLargeError("The uploaded file exceeds the byte limit.")

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(data)) as probe:
                _validate_dimensions(probe)
                if probe.format not in SUPPORTED_FORMATS:
                    raise InvalidImageError("The image format is not supported.")
                actual_format = probe.format
                probe.verify()

            with Image.open(BytesIO(data)) as source:
                _validate_dimensions(source)
                if source.format != actual_format:
                    raise InvalidImageError("The image format could not be verified.")
                source.load()
                oriented = ImageOps.exif_transpose(source)
                normalized = _to_rgb(oriented)
                normalized.thumbnail(
                    (MAX_IMAGE_SIDE, MAX_IMAGE_SIDE),
                    Image.Resampling.LANCZOS,
                )
                output = BytesIO()
                normalized.save(output, format="JPEG", quality=90, optimize=True)
                return PreparedImage(data=output.getvalue())
    except (ImageTooLargeError, InvalidImageError):
        raise
    except (Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise ImageTooLargeError("The image dimensions exceed the pixel limit.") from exc
    except (
        EOFError,
        OSError,
        SyntaxError,
        UnidentifiedImageError,
        ValueError,
    ) as exc:
        raise InvalidImageError("The upload is not a valid supported image.") from exc


def _validate_dimensions(image: Image.Image) -> None:
    width, height = image.size
    if width <= 0 or height <= 0 or width * height > MAX_IMAGE_PIXELS:
        raise ImageTooLargeError("The image dimensions exceed the pixel limit.")


def _to_rgb(image: Image.Image) -> Image.Image:
    has_alpha = "A" in image.getbands() or "transparency" in image.info
    if not has_alpha:
        return image.convert("RGB")

    rgba = image.convert("RGBA")
    background = Image.new("RGB", rgba.size, (255, 255, 255))
    background.paste(rgba, mask=rgba.getchannel("A"))
    return background