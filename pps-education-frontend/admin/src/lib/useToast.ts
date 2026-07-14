import { useState } from "react";

export function useToast(durationMs = 4000) {
  const [message, setMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setMessage(msg);
    setTimeout(() => setMessage((current) => (current === msg ? null : current)), durationMs);
  };

  return { message, showToast };
}
