-- Token Bucket algorithm, run atomically inside Redis.
--
-- Why Lua here specifically: this algorithm has to READ two values (tokens,
-- last_refill), do MATH on them, then WRITE the result back. If that read-modify-
-- write happened as three separate Redis calls from Java, two requests arriving
-- at the same millisecond could both read the same starting state and both think
-- they're allowed. Wrapping it in one Lua script makes the whole sequence a single
-- atomic step — Redis will not run any other command in the middle of it.
--
-- KEYS[1] = the bucket's Redis key (one per client)
-- ARGV[1] = capacity (max tokens the bucket can hold)
-- ARGV[2] = refill rate (tokens added per second)
-- ARGV[3] = current time in milliseconds (passed in from Java, not read from
--           inside Lua — Lua scripts must be deterministic for Redis replication,
--           and the server's own clock could differ between primary/replica)

local key         = KEYS[1]
local capacity    = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])

local data        = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens      = tonumber(data[1])
local last_refill = tonumber(data[2])

-- First time we've seen this client: start their bucket full.
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- How many tokens should have regenerated since we last saw this client?
local elapsed_seconds = (now - last_refill) / 1000.0
local refill_amount = elapsed_seconds * refill_rate

-- Add the refill, but never go above capacity (otherwise an idle client would
-- accumulate unlimited tokens and get a huge burst whenever they came back).
tokens = math.min(capacity, tokens + refill_amount)

local allowed = 0
local retry_after_ms = 0

if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    -- Not enough tokens. Work out how long until there's at least 1.
    local deficit = 1 - tokens
    retry_after_ms = math.ceil((deficit / refill_rate) * 1000)
end

-- Always save state back, even on a denial — otherwise the next request
-- recalculates refill from stale data and the client gets tokens they shouldn't.
redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(now))
redis.call('EXPIRE', key, 3600) -- cleanup: forget idle clients after an hour

return { allowed, math.floor(tokens), retry_after_ms }
