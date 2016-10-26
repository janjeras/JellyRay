#define NULL                0
#define X_DIMENSION         0
#define Y_DIMENSION         1
#define ANTI_ALIASING_DEPTH 3
#define ANTI_ALIASING_COUNT (ANTI_ALIASING_DEPTH * ANTI_ALIASING_DEPTH)
#define MAX_RAY_DEPTH       3
#define SPECULAR_POWER      50
#define SPECULAR_CONSTANT   0.1f
#define DIFFUSE_CONSTANT    1

#define BACKGROUND_COLOR    (float4)(0, 0, 0, 0)
#define RED                 (float4)(1, 0, 0, 1)
#define GREEN               (float4)(0, 1, 0, 1)
#define BLUE                (float4)(0, 0, 1, 1)
#define CYAN                (float4)(0, 1, 1, 1)
#define MAGENTA             (float4)(1, 0, 1, 1)
#define YELLOW              (float4)(1, 1, 0, 1)
#define WHITE               (float4)(1, 1, 1, 1)
#define BLACK               (float4)(0, 0, 0, 1)

float3 MultiplyVectorByMatrix(float3 vector, const global float* matrix) {
    float3 result;
    result.x = vector.x * matrix[0] + vector.y * matrix[1] + vector.z * matrix[2] + matrix[3];
    result.y = vector.x * matrix[4] + vector.y * matrix[5] + vector.z * matrix[6] + matrix[7];
    result.z = vector.x * matrix[8] + vector.y * matrix[9] + vector.z * matrix[10] + matrix[11];
    return result;
}

float3 Reflect(float3 incident, float3 normal) {
    return incident - 2.0f * dot(incident, normal) * normal;
}



// ------------------------------------------------------------
// Material
// ------------------------------------------------------------
typedef struct Material {
    float4 color;
    float reflectivity;
    float refractivity;
} Material;

Material CreateMaterial(float4 color, float reflectivity, float refractivity) {
    Material material;
    material.color = color;
    material.reflectivity = reflectivity;
    material.refractivity = refractivity;
    return material;
}



// ------------------------------------------------------------
// Ray
// ------------------------------------------------------------
typedef struct RayStub {
    float3 origin;
    float3 direction;
} RayStub;

typedef struct Ray {
    float3 origin;
    float3 direction;
    float3 reciprocalDirection;
    int depth;
    float t;
    float3 intersection;
    float3 normal;
    Material* material;
} Ray;

Ray CreateRay(float3 origin, float3 direction) {
    Ray ray;
    ray.origin = origin;
    ray.direction = normalize(direction);
    ray.reciprocalDirection = (float3)(1, 1, 1) / ray.direction;
    ray.depth = 0;
    ray.t = INFINITY;
    ray.intersection = (float3)(0, 0, 0);
    ray.normal = (float3)(0, 0, 0);
    ray.material = NULL;
    return ray;
}

void CreatePrimaryRays(RayStub* result,
                       int globalIdX, int globalIdY,
                       int displayWidth, int displayHeight,
                       float aspectRatio, float tanHalfFieldOfView,
                       const global float* cameraToWorldMatrix) {

    float x = (float)globalIdX + 1.0f / (2.0f * ANTI_ALIASING_DEPTH);
    float y = (float)globalIdY + 1.0f / (2.0f * ANTI_ALIASING_DEPTH);
    float offset = 1.0f / (float)ANTI_ALIASING_DEPTH;

    for(int i = 0; i < ANTI_ALIASING_DEPTH; ++i) {
        for(int j = 0; j < ANTI_ALIASING_DEPTH; ++j) {
            float normalizedDeviceCoordinatesX = (x + j * offset) / (float)displayWidth;
            float normalizedDeviceCoordinatesY = (y + i * offset) / (float)displayHeight;

            float cameraX = (2.0f * normalizedDeviceCoordinatesX - 1.0f) * aspectRatio * tanHalfFieldOfView;
            float cameraY = (2.0f * normalizedDeviceCoordinatesY - 1.0f) * tanHalfFieldOfView;

            float3 origin = (float3)(0, 0, 0);
            float3 raster = (float3)(-cameraX, cameraY, -1);

            origin = MultiplyVectorByMatrix(origin, cameraToWorldMatrix);
            raster = MultiplyVectorByMatrix(raster, cameraToWorldMatrix);

            result[i + j * ANTI_ALIASING_DEPTH].origin = origin;
            result[i + j * ANTI_ALIASING_DEPTH].direction = raster - origin;
        }
    }
}

Ray CreateReflectionRay(Ray* parent) {
    Ray ray = CreateRay(parent->intersection, Reflect(parent->direction, parent->normal));
    ray.depth = parent->depth + 1;
    return ray;
}



// ------------------------------------------------------------
// Light
// ------------------------------------------------------------
typedef struct Light {
    float3 origin;
    float3 direction;
    float4 color;
} Light;

Light CreateLight(float3 origin, float3 direction, float4 color) {
    Light light;
    light.origin = origin;
    light.direction = direction != NULL ? normalize(direction) : NULL;
    light.color = color;
    return light;
}



// ------------------------------------------------------------
// Sphere
//
// CLBuffer<FloatBuffer>:
// [center.x, center.y, center.z, radius,
//  material.color.x, material.color.y, material.color.z, material.color.w,
//  material.reflectivity, material.refractivity]
// ------------------------------------------------------------
typedef struct Sphere {
    float3 center;
    float radius;
    float radiusSquared;
    Material* material;
} Sphere;

Sphere CreateSphere(float3 center, float radius, Material material) {
    Sphere sphere;
    sphere.center = center;
    sphere.radius = radius;
    sphere.radiusSquared = radius * radius;
    sphere.material = &material;
    return sphere;
}

void TraceSphere(Ray* ray, Sphere* sphere) {

    float3 distance = sphere->center - ray->origin;
    float distanceSquared = dot(distance, distance);
    if(distanceSquared <= sphere->radiusSquared)
        return;     // ray origin is within or on sphere

    float closestApproach = dot(distance, ray->direction);
    if(closestApproach <= 0)
        return;     // sphere center lies behind ray origin, as ray origin lies
                    // outside the sphere (see previous return) it can not intersect

    float halfChordDistanceSquared = sphere->radiusSquared - distanceSquared +
                                     closestApproach * closestApproach;
    if(halfChordDistanceSquared < 0)
        return;     // ray misses the sphere

    float t = closestApproach - sqrt(halfChordDistanceSquared);
    if(t < ray->t) {
        ray->t = t;
        ray->intersection = ray->origin + t * ray->direction - ray->direction * 0.0001f;
        ray->normal = normalize(ray->intersection - sphere->center);
        ray->material = sphere->material;
    }
}



// ------------------------------------------------------------
// Box
// ------------------------------------------------------------
typedef struct Box {
    float3 minimumExtent;
    float3 maximumExtent;
    float3 center;
    Material* material;
} Box;

Box CreateBox(float3 minimumExtent, float3 maximumExtent, Material material) {
    Box box;
    box.minimumExtent = minimumExtent;
    box.maximumExtent = maximumExtent;
    box.center = (float3)((minimumExtent.x + maximumExtent.x) * 0.5f,
                          (minimumExtent.y + maximumExtent.y) * 0.5f,
                          (minimumExtent.z + maximumExtent.z) * 0.5f);
    box.material = &material;
    return box;
}

void TraceBoxSlab(float minimumExtent, float maximumExtent,
                   float origin, float reciprocalDirection,
                   float* near, float* far) {
    float t1 = (minimumExtent - origin) * reciprocalDirection;
    float t2 = (maximumExtent - origin) * reciprocalDirection;
    bool flip = t1 > t2;
    float t1temp = t1;
    t1 = flip ? t2 : t1;
    t2 = flip ? t1temp : t2;
    *near = t1 > *near ? t1 : *near;
    *far = t2 < *far ? t2 : *far;
}

void TraceBox(Ray* ray, Box* box) {
    float near = -INFINITY;
    float far = INFINITY;

    TraceBoxSlab(box->minimumExtent.x, box->maximumExtent.x,
                  ray->origin.x, ray->reciprocalDirection.x,
                  &near, &far);
    if(near > far || far < 0)
        return;

    TraceBoxSlab(box->minimumExtent.y, box->maximumExtent.y,
                  ray->origin.y, ray->reciprocalDirection.y,
                  &near, &far);
    if(near > far || far < 0)
        return;

    TraceBoxSlab(box->minimumExtent.z, box->maximumExtent.z,
                  ray->origin.z, ray->reciprocalDirection.z,
                  &near, &far);
    if(near > far || far < 0)
        return;

    if(near < ray->t) {
        ray->t = near;
        ray->intersection = ray->origin + near * ray->direction;

        float min = INFINITY;
        float3 direction = ray->intersection - box->center;
        for(int i = 0; i < 3; ++i) {
            float distance = fabs(box->minimumExtent[i] - fabs(direction[i]));
            if(distance < min) {
                min = distance;
                ray->normal = (float3)(0, 0, 0);
                ray->normal[i] = signbit(direction[i]) ? -1  : 1;
            }
        }

        ray->intersection += ray->normal * 0.0001f;
        ray->material = box->material;
    }
}



// ------------------------------------------------------------
// Plane
// ------------------------------------------------------------
typedef struct Plane {
    float3 normal;
    float distance;
    Material* material;
} Plane;

Plane CreatePlane(float3 normal, float distance, Material material) {
    Plane plane;
    plane.normal = normalize(normal);
    plane.distance = distance;
    plane.material = &material;
    return plane;
}

void TracePlane(Ray* ray, Plane* plane) {
    float direction = dot(plane->normal, ray->direction);
    if(direction >= 0)
        return;     // if 0 ray is parallel to plane
                    // if > 0 plane normal points away from ray

    float origin = -(dot(plane->normal, ray->origin) + plane->distance);
    float t = origin / direction;
    if(t <= 0)
        return;     // ray intersects plane behind or on origin

    if(t < ray->t) {
        ray->t = t;
        ray->intersection = ray->origin + t * ray->direction;
        ray->normal = plane->normal;
        //ray->material = plane->material;

        // checkered plane hack
        Material testWhite = CreateMaterial((float4)(1, 1, 1, 1), 0.1f, 0);
        Material testBlack = CreateMaterial((float4)(0, 0, 0, 0), 0.1f, 0);
        float3 p = ray->origin + t * ray->direction;
        if(plane->normal.y == 1) {
            int3 point = (int3)(round(p.x * 0.1f), 0, round(p.z * 0.1f));
            ray->material = abs(point.x) % 2 == abs(point.z) % 2 ? &testWhite : &testBlack;
        } else {
            int3 point = (int3)(0, round(p.y * 0.1f) + 1, round(p.z * 0.1f));
            ray->material = abs(point.y) % 2 == abs(point.z) % 2 ? &testWhite : &testBlack;
        }
    }
}



// ------------------------------------------------------------
// Triangle
// ------------------------------------------------------------
typedef struct Triangle {
    float3 vertex1;
    float3 vertex2;
    float3 vertex3;
} Triangle;

void TraceTriangle(Ray ray, Triangle triangle) {

}



// ------------------------------------------------------------
// Scene
// ------------------------------------------------------------
float4 TraceScene(Ray* ray,
                  Plane* planes, int planeCount,
                  Sphere* spheres, int sphereCount) {

    for(int i = 0; i < planeCount; ++i) {
        TracePlane(ray, &planes[i]);
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    for(int i = 0; i < sphereCount; ++i) {
        TraceSphere(ray, &spheres[i]);
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    float4 color = ray->material == NULL ? BACKGROUND_COLOR : ray->material->color;
    return color;
}

bool QuickTraceScene(Ray* ray, float minT,
                     Plane* planes, int planeCount,
                     Sphere* spheres, int sphereCount) {

    for(int i = 0; i < planeCount; ++i) {
        TracePlane(ray, &planes[i]);
        if(ray->t < minT)
            return false;
    }

    for(int i = 0; i < sphereCount; ++i) {
        TraceSphere(ray, &spheres[i]);
        if(ray->t < minT)
            return false;
    }

    return true;
}



// ------------------------------------------------------------
// Kernel
// ------------------------------------------------------------
kernel void RayTracer(const int displayWidth, const int displayHeight,
                      const float aspectRatio, const float tanHalfFieldOfView,
                      const global float* cameraToWorldMatrix,
                      global float* output) {

    int globalIdX = get_global_id(X_DIMENSION);
    if(globalIdX >= displayWidth)
        return;
    int globalIdY = get_global_id(Y_DIMENSION);
    if(globalIdY >= displayHeight)
        return;

    RayStub primaryRays[ANTI_ALIASING_COUNT];
    CreatePrimaryRays(primaryRays,
                      globalIdX, globalIdY,
                      displayWidth, displayHeight,
                      aspectRatio, tanHalfFieldOfView,
                      cameraToWorldMatrix);

    int localIdX = get_local_id(X_DIMENSION);
    int localIdY = get_local_id(Y_DIMENSION);
    int localId = localIdX + localIdY * get_local_size(X_DIMENSION);
    int localSize = get_local_size(X_DIMENSION) * get_local_size(Y_DIMENSION);

    const int lightCount = 1;
    Light lights[lightCount];
    lights[0] = CreateLight((float3)(0, 50, 0), NULL, (float4)(1, 1, 1, 1));

    const int planeCount = 1;
    Plane planes[planeCount];
    planes[0] = CreatePlane((float3)(0, 1, 0), 15, CreateMaterial((float4)(1, 1, 1, 1), 0.1f, 0));

    const int sphereCount = 7;
    Sphere spheres[sphereCount];
    spheres[0] = CreateSphere((float3)(0, -13, -10), 2, CreateMaterial(RED, 0.33f, 0));
    spheres[1] = CreateSphere((float3)(-4, -13, -10), 2, CreateMaterial(GREEN, 0.33f, 0));
    spheres[2] = CreateSphere((float3)(-8, -13, -10), 2, CreateMaterial(BLUE, 0.33f, 0));
    spheres[3] = CreateSphere((float3)(0, -13, -6), 2, CreateMaterial(CYAN, 0.33f, 0));
    spheres[4] = CreateSphere((float3)(-4, -13, -6), 2, CreateMaterial(MAGENTA, 0.33f, 0));
    spheres[5] = CreateSphere((float3)(-8, -13, -6), 2, CreateMaterial(YELLOW, 0.33f, 0));
    spheres[6] = CreateSphere((float3)(10, -11, -5), 4, CreateMaterial((float4)(1, 0, 0, 1), 0.1f, 0));

    float4 pixel = BACKGROUND_COLOR;
    for(int i = 0; i < ANTI_ALIASING_COUNT; ++i) {
        Ray ray  = CreateRay(primaryRays[i].origin, primaryRays[i].direction);

        float4 color = BACKGROUND_COLOR;

        bool isReflection = false;
        float reflectivity = 0;
        bool isRefraction = false;
        float refractivity = 0;
        while(ray.depth <= MAX_RAY_DEPTH) {
            float4 c = TraceScene(&ray,
                                  planes, planeCount,
                                  spheres, sphereCount);
            barrier(CLK_LOCAL_MEM_FENCE);

            for(int i = 0; i < lightCount; ++i) {
                float3 distance = lights[i].origin - ray.intersection;
                Ray lightRay = CreateRay(ray.intersection, distance);
                float minT = distance[0] * lightRay.reciprocalDirection[0];
                if(QuickTraceScene(&lightRay, minT,
                                    planes, planeCount,
                                    spheres, sphereCount)) {
                    //phong
                    c *= dot(ray.normal, lightRay.direction) * lights[i].color; // diffuse
                    float3 reflect = Reflect(-lightRay.direction, ray.normal);
                    float3 specular = pow(max(0.f, dot(reflect, -ray.direction)), SPECULAR_POWER);
                    c.x += specular.x * SPECULAR_CONSTANT;
                    c.y += specular.y * SPECULAR_CONSTANT;
                    c.z += specular.z * SPECULAR_CONSTANT;
                } else { // shadow ray
                    c = (float4)(0, 0, 0, 0);
                }
            }

            if(isReflection) {
                float factor = pow(reflectivity, ray.depth);
                color += factor * c;
                color /= 1 + factor;
            } else
                color += c;

            if(ray.material != NULL && ray.material->reflectivity > 0) {
                isReflection = true;
                reflectivity = ray.material->reflectivity;
                ray = CreateReflectionRay(&ray);
            } else {
                isReflection = false;
                ++ray.depth;
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        pixel += color / (float)ANTI_ALIASING_COUNT;
    }

    pixel = clamp(pixel, (float4)(0, 0, 0, 0), (float4)(1, 1, 1, 1));
    int offset = (globalIdX + globalIdY * displayWidth) * 3;
    output[offset++] = pixel.x;
    output[offset++] = pixel.y;
    output[offset  ] = pixel.z;
}
