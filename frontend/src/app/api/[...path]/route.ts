import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.API_BACKEND_URL || "http://localhost:8080";

const ALLOWED_PATH_PREFIXES = ["documents", "search", "health", "actuator"];

export async function GET(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, await params, "GET");
}

export async function POST(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, await params, "POST");
}

export async function DELETE(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  return handleProxy(request, await params, "DELETE");
}

async function handleProxy(
  request: NextRequest,
  params: { path: string[] },
  method: string
) {
  const pathArray = params.path || [];
  const primarySegment = pathArray[0] || "";

  // Validate allowed paths (Security finding prevention: No open proxy)
  if (!ALLOWED_PATH_PREFIXES.includes(primarySegment)) {
    return NextResponse.json(
      {
        type: "https://docsearch.deeprunner.com/errors/forbidden-proxy-path",
        title: "Proxy path not allowed",
        status: 403,
        detail: `Path '/${pathArray.join("/")}' is not in proxy allow-list`,
        code: "PROXY_PATH_FORBIDDEN",
        timestamp: new Date().toISOString(),
      },
      { status: 403 }
    );
  }

  const targetPath = "/" + pathArray.join("/");
  const searchParams = request.nextUrl.searchParams.toString();
  const url = `${BACKEND_URL}${targetPath}${searchParams ? `?${searchParams}` : ""}`;

  // Read tenant from cookie or header (authoritative inject)
  const tenantCookie = request.cookies.get("tenant_id")?.value;
  const clientTenantHeader = request.headers.get("X-Tenant-ID");
  const tenantId = tenantCookie || clientTenantHeader || "acme";

  const headers: Record<string, string> = {
    "X-Tenant-ID": tenantId,
  };

  const clientRequestId = request.headers.get("X-Request-Id");
  if (clientRequestId) {
    headers["X-Request-Id"] = clientRequestId;
  }

  let body: string | undefined = undefined;
  if (method === "POST") {
    headers["Content-Type"] = request.headers.get("Content-Type") || "application/json";
    body = await request.text();
  }

  try {
    const backendResponse = await fetch(url, {
      method,
      headers,
      body,
      cache: "no-store",
    });

    const responseHeaders = new Headers();
    backendResponse.headers.forEach((val, key) => {
      if (
        key.toLowerCase().startsWith("x-ratelimit-") ||
        key.toLowerCase() === "x-request-id" ||
        key.toLowerCase() === "location" ||
        key.toLowerCase() === "retry-after"
      ) {
        responseHeaders.set(key, val);
      }
    });
    responseHeaders.set("Content-Type", backendResponse.headers.get("Content-Type") || "application/json");

    const responseBody = await backendResponse.text();
    return new NextResponse(responseBody, {
      status: backendResponse.status,
      headers: responseHeaders,
    });
  } catch (error: any) {
    return NextResponse.json(
      {
        type: "https://docsearch.deeprunner.com/errors/gateway-timeout",
        title: "Backend connection failure",
        status: 504,
        detail: `Failed to reach upstream backend service at ${BACKEND_URL}: ${error.message}`,
        code: "BACKEND_UNAVAILABLE",
        timestamp: new Date().toISOString(),
      },
      { status: 504 }
    );
  }
}
