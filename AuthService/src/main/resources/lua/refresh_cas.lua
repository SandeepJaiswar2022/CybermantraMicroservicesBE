-- KEYS[1] = token_family:{familyId}
-- ARGV[1] = incomingHash
-- ARGV[2] = newHash
-- ARGV[3] = newIdleEpochSeconds
-- ARGV[4] = nowEpochSeconds
-- returns string codes: OK | NOT_FOUND | REVOKED | MAX_EXPIRED | IDLE_EXPIRED | HASH_MISMATCH_REVOKED

local key = KEYS[1]
local exists = redis.call("EXISTS", key)
if exists == 0 then
  return "NOT_FOUND"
end

local is_revoked = redis.call("HGET", key, "is_revoked")
if is_revoked == "1" or is_revoked == "true" then
  return "REVOKED"
end

local max_expiry = tonumber(redis.call("HGET", key, "max_expiry") or "0")
local idle_expiry = tonumber(redis.call("HGET", key, "idle_expiry") or "0")
local now = tonumber(ARGV[4])

if now > max_expiry then
  -- max lifetime exceeded
  redis.call("HSET", key, "is_revoked", "1")
  return "MAX_EXPIRED"
end

if now > idle_expiry then
  redis.call("HSET", key, "is_revoked", "1")
  return "IDLE_EXPIRED"
end

local currentHash = redis.call("HGET", key, "current_refresh_token_hash")
if currentHash ~= ARGV[1] then
  -- possible token reuse: revoke family
  redis.call("HSET", key, "is_revoked", "1")
  return "HASH_MISMATCH_REVOKED"
end

-- Update hash and idle expiry and last_used_at atomically
redis.call("HSET", key,
    "current_refresh_token_hash", ARGV[2],
    "idle_expiry", ARGV[3],
    "last_used_at", ARGV[4]
)
return "OK"
