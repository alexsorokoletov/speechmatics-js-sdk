# Speechmatics SDKs (Unofficial)

> **Note:** These are unofficial, community-maintained SDKs for Speechmatics APIs. They are not affiliated with or endorsed by Speechmatics Ltd. For official SDKs, visit [speechmatics.com](https://speechmatics.com).

This monorepo contains unofficial client SDKs for Speechmatics speech recognition APIs.

## SDKs

### JavaScript/TypeScript

Located in [`js/`](./js/):

- **Batch client**: Upload files for transcription
  [`@speechmatics/batch-client`](./js/packages/batch-client)
- **Real-time client**: Stream audio for live transcription
  [`@speechmatics/real-time-client`](./js/packages/real-time-client)
- **Flow client**: Conversational AI voice assistant
  [`@speechmatics/flow-client`](./js/packages/flow-client)
- **Flow client React**: React hooks for Flow
  [`@speechmatics/flow-client-react`](./js/packages/flow-client-react)

### Android (Kotlin)

Located in [`android/`](./android/):

- **speechmatics-android**: Full-featured Android SDK
  - Batch, Real-time, and Flow APIs
  - Audio recording and playback utilities
  - Kotlin coroutines and Flow support

See [android/README.md](./android/README.md) for installation and usage.

## Documentation

- [Speechmatics API Docs](https://docs.speechmatics.com/)
- [Examples](./js/examples/)

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

MIT License

## Disclaimer

This project is not affiliated with, endorsed by, or connected to Speechmatics Ltd.
"Speechmatics" is a trademark of Speechmatics Ltd.
