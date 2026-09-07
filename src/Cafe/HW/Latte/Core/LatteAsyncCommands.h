#pragma once

void LatteAsyncCommands_queueForceTextureReadback(MPTR physAddr, MPTR mipAddr, uint32 swizzle, sint32 format, sint32 width, sint32 height, sint32 depth, uint32 pitch, uint32 slice, sint32 dim, Latte::E_HWTILEMODE tilemode, sint32 aa, sint32 level);
void LatteAsyncCommands_queueDeleteShader(uint64 shaderBaseHash, uint64 shaderAuxHash, LatteConst::ShaderType shaderType);
// Fully invalidates a shader so the next draw recompiles it - used for live graphic pack switching.
// Unlike queueDeleteShader (which only unlinks it from the lookup table) this frees the shader and
// cascades to its pipelines and their cached descriptor sets. That cascade is required because the
// pipeline cache is keyed by shader *baseHash*, so a recompiled shader with the same hash would
// otherwise be handed back the old, stale pipeline.
void LatteAsyncCommands_queueReloadShader(uint64 shaderBaseHash, uint64 shaderAuxHash, LatteConst::ShaderType shaderType);
void LatteAsyncCommands_waitUntilAllProcessed();

void LatteAsyncCommands_checkAndExecute();